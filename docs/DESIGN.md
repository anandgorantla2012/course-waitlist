# Design Specification: Course Waitlist

## 1. Scope and service boundary
One offering: enroll, drop, leave waitlist, refresh waitlist, snapshot. Out of scope: frontend, catalog,
payments, identity, multi-term, deployment, database, configurable policy engine.

```
EnrollmentService  (public API, owns the lock, atomic operations)
   |-- EnrollmentRoster        (domain: enrolled set + waitlist, structural rules only)
   |-- EligibilityPolicy       (prerequisite subset check)
   '-- LearnerRecords (port)   (completed courses; in-memory adapter provided)
```
The only external dependency is `LearnerRecords`. It is a port because eligibility is re-evaluated at
promotion time and a learner's records can change between enrollment and promotion.

## 2. Assumptions and policies
| # | Assumption | Decision |
|---|---|---|
| A1 | One offering, fixed capacity >= 1 | Constructors reject <= 0 |
| A2 | Eligible = every prerequisite course is in the learner's completed set | Subset check; no grades, no corequisites |
| A3 | Ineligible at request time | `Ineligible(missing)`; not enrolled, not waitlisted, no state change |
| A4 | **Ineligible at the front of the queue at promotion time** | **Skipped and retained** at original position; the next eligible waiter is promoted |
| A5 | No queue-jumping | Free seats are filled from the waitlist before any new enroll request is evaluated |
| A6 | A learner is ENROLLED or WAITLISTED, never both | Enforced by roster mutators |
| A7 | Duplicate enroll | `AlreadyEnrolled` / `AlreadyWaitlisted(position)`; no state change, no new position |
| A8 | Drop applies only to enrolled learners | Others get `NotEnrolled` (a waitlisted-only learner stays waitlisted); a repeated drop is a no-op and never frees a second seat |
| A9 | Waitlisted learner withdrawing | Separate `leaveWaitlist()`; not overloaded onto drop |
| A10 | Learner has no record | `UnknownLearner`; no state change. A waiter whose record later disappears is treated as ineligible (skipped, retained). On drop, `UnknownLearner` is returned only when the learner is not enrolled, not waitlisted and has no record; an enrolled learner can always be dropped |

Why skip-and-retain over remove: removing someone because of a transient records problem is a silent
data-loss decision. Retaining keeps fairness and is reversible. Cost: O(waitlist) rescans.

## 3. Data design
- **Enrolled**: `LinkedHashSet<LearnerId>`; O(1) add/remove/contains.
- **Waitlist**: `LinkedHashMap<LearnerId, WaitlistEntry(learner, sequence)>`. One structure gives O(1)
  duplicate check, O(1) mid-queue removal and FIFO iteration, so "no duplicates" is structural instead of
  needing a second set kept in sync. The sequence number is a monotonic tie-break/audit value tested directly.
- **Snapshot**: `RosterSnapshot` record of defensive copies; value equality lets tests assert "no state change".

## 4. API
`EnrollResult`: `Enrolled`, `Waitlisted(position)`, `AlreadyEnrolled`, `AlreadyWaitlisted(position)`,
`Ineligible(missing)`, `UnknownLearner`.
`DropResult`: `Dropped(learner, promoted: List)`, `NotEnrolled`, `UnknownLearner`.
Expected business outcomes are results, not exceptions; exceptions (`NullPointerException`,
`IllegalArgumentException`) are for programmer errors (null ids, non-positive capacity).
`Waitlisted(position)` and `AlreadyWaitlisted(position)` carry the 1-based queue position among everyone currently waiting. Skipped
(ineligible) waiters count toward it, so position is queue position, not a promise of promotion order; positions shift up when
an earlier waiter leaves.
Constructors validate arguments: a null offering fails with `offering must not be null`; ids must be non-blank.
`promoted` is a list rather than an Optional because a drop can fill more than one seat if skipped waiters
became eligible since the last refresh.

## 5. Invariants
| ID | Invariant |
|---|---|
| I1 | enrolled.size() <= capacity |
| I2 | enrolled and waitlist are disjoint |
| I3 | no duplicates in the waitlist |
| I4 | waitlist sequence numbers strictly increase in queue order |
| I5 | everyone enrolled was eligible when enrolled |
| I6 | work-conserving: if a seat is free, no waitlisted learner is currently eligible |
| I7 | an invalid or repeated operation leaves the snapshot equal to the before-snapshot |

I6 holds after every service call and after `refreshWaitlist()`. If records change and nobody calls the
service, a free seat can transiently coexist with a newly eligible waiter; the next `enroll`, `drop` or
`refreshWaitlist()` reconciles first (A5). I7 holds whenever records have not changed since the last call. Because every `enroll` reconciles first (A5), a duplicate request
after a records change may promote a waiter before returning `AlreadyEnrolled`/`AlreadyWaitlisted`; this is deliberate, and a
promoted waiter who calls `enroll` again gets `AlreadyEnrolled`. `RosterInvariants` in test support asserts I1-I6; I5 is only
statically checkable when records never lose courses, which the randomized test guarantees.

## 6. Promotion algorithm
```
while enrolled.size() < capacity:
    candidate = first waitlist entry (queue order) that is eligible NOW
    if none: break
    move candidate waitlist -> enrolled
```
Complexity O(k*w) for k open seats and w waiters. The scan restarts from the head each time on purpose:
eligibility is dynamic, so a previously skipped learner who qualifies again regains original priority.

## 7. Concurrency
A single `ReentrantLock` per service (single offering => no lock ordering). Every public method, reads
included, holds it; reads return immutable copies. Drop and promotion happen in one critical section.
`fillOpenSeats` is private and documented as requiring the lock.
Tradeoff: `LearnerRecords` is called while holding the lock. Fine for the in-memory adapter; for I/O-backed
records prefetch or use optimistic versioning.
Re-entrancy rule: `ReentrantLock` lets the same thread re-enter, so a `LearnerRecords` implementation must never call back into
the service from `completedCourses`; doing so would modify the waitlist while it is being iterated.
Failure handling: if `LearnerRecords` throws during `enroll`, state is unchanged and the lock is released (tested from another
thread). If it throws while `drop` is promoting, the drop has already taken effect but no promotion happened: the caller sees the
exception, the seat stays open, nothing is corrupted, and the next call or `refreshWaitlist()` reconciles. The exception is not
swallowed, because hiding a records failure would be worse.
Production mapping (not built): `SELECT ... FOR UPDATE` on the offering row, unique constraint on
(offering_id, learner_id), `waitlist_seq` sequence for FIFO, one transaction for drop + promote.

## 8. Verification strategy
Acceptance-criterion-mapped unit tests; seeded randomized test (3 seeds x 10,000 ops, invariants after
every step, seed in the failure message); concurrency tests (64 threads behind a start gate, repeated 50x);
a mutation check (remove the lock from `enroll` and confirm the concurrency tests fail), and edge-case tests found by reviewing the
test matrix and the code (multi-seat promotion, refresh no-op, empty prerequisites, shifting positions, records failures, null offering).

Results on JDK 22 (compiled with `release 21`): `mvn clean verify` -> 186 tests, 0 failures, `BUILD SUCCESS`. With the lock removed
from `enroll`, 125 of 150 concurrency runs failed (6 learners in a capacity-5 course, lost waitlist entries, duplicate sequence
numbers, duplicate enrollment). Concurrency tests are probabilistic: they show the lock works and that the tests detect its absence,
not that races are impossible.

Known limitations and follow-up risks are listed in [LIMITATIONS.md](LIMITATIONS.md).
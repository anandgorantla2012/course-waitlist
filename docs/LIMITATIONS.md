# Limitations and Follow-up Risks
- In-memory only; state is lost on restart.
- One process; the lock does not span JVMs.
- Skip-and-retain rescans from the head: O(k*w) per drop. Fine at classroom scale.
- Eligibility is a prerequisite subset check only (no grades, corequisites, holds, or time-limited credit).
- Waitlist position counts skipped (ineligible) waiters, so a reported position is not a guaranteed
  promotion order.
- Promotion produces no notification; callers learn of it only from `Dropped.promoted` or `refreshWaitlist()`.
- Changes to learner records are not observed automatically. They are picked up by `refreshWaitlist()`,
  by the next `enroll` (which reconciles first), or by a successful `drop`. `leaveWaitlist` and a no-op
  `drop` (learner not enrolled) do not reconcile.
- `LearnerRecords` is called while holding the lock (I/O under a lock in a real adapter).
- Concurrency tests are probabilistic: they showed the lock works and failed when it was removed, but
  cannot prove absence of races.
- Single offering only; multiple offerings would need a per-offering lock map and lock ordering.
- The Maven wrapper (`mvnw`) is not committed; generate it with `mvn -N wrapper:wrapper`.
- If `LearnerRecords` throws during promotion inside `drop`, the drop has already taken effect but the
  promotion has not: the caller sees an exception, the seat stays open, nothing is corrupted, and the next
  `enroll`, successful `drop`, or `refreshWaitlist()` reconciles it (covered by `EdgeCaseTest`).
- `ReentrantLock` is re-entrant: a `LearnerRecords` implementation that calls back into the service from
  inside `completedCourses` would run while the waitlist is being iterated. Adapters must not do this.
- Learner ids are case-sensitive strings (`Alice` and `alice` are different learners).
- A duplicate `enroll` request made after a records change may first promote a waiter (reconcile runs
  before the duplicate check). A promoted waiter who calls `enroll` again gets `AlreadyEnrolled`, which is
  correct but does not say that a promotion just happened.
- No waitlist size limit; the waitlist can grow without bound.
- Position lookup is O(w) because the waitlist is a linked map without an index.
- No coverage or mutation-testing tooling (JaCoCo, PIT); mutation checking was done manually by removing
  the lock.
- Any-of prerequisites (extension): `Ineligible.missing` lists every option, not just one; there is no support for mixed rules such as
  "(A or B) and C", and the policy is chosen once at construction (it cannot differ per learner).
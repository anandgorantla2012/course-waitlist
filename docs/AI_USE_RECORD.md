# AI Prompt and Use Record

AI use in this project, in the order it happened:
0. Planning (assessment brief -> implementation plan)
1. Implementation from the plan
2. Test-matrix comparison
3. Code review (same AI)
4. Independent code review (separate AI chat)

---

## Entry 0: Planning (before any implementation)
- **Input given to AI:** the assessment brief (`BRAHMANANDAM_GORANTLA_AI_Assessment.pdf`).
- **Prompt (scrubbed):** "Create a Java implementation plan for the attached assessment ..."
- **AI response (summary):** `Java_Implementation_Plan.docx`, covering: tech stack (Java 21, Maven,
  JUnit 5 + AssertJ, no frameworks), project layout, assumptions A1-A10, data structures
  (`LinkedHashSet` for enrolled, `LinkedHashMap` + sequence numbers for the waitlist), a promotion
  algorithm, a single-lock concurrency design, a sealed-result API, invariants I1-I7, a test plan mapped
  to the acceptance criteria, an AI-use record outline, a schedule, pitfalls and limitations.
- **Decisions on suggestions**
  | Plan item | Decision | Reason |
  |---|---|---|
  | Plain Java 21 + Maven, no Spring/DB/Kafka | Accepted | Brief says no new database or extra infrastructure; keeps the slice bounded |
  | Skip-and-retain for an ineligible waiter at the front (A4) | Accepted | Avoids silent data loss; policy is documented and applied in one place |
  | Reconcile before enroll, no queue-jumping (A5) | Accepted | Prevents a late arrival taking a seat an eligible waiter is entitled to |
  | `leaveWaitlist()` (A9, marked optional) | Accepted | Small, and avoids overloading `drop` |
  | JaCoCo and PIT (marked optional) | Deferred / not done | Time; mutation check done manually instead (see Entry 1) |
  | Entry-point `Main` demo (optional) | Accepted | Quick runnable demonstration |
  | Plan's AI-record section told me not to fabricate entries | Accepted | This record contains only what was actually done |
- **Files/decisions informed:** `docs/DESIGN.md` (assumptions, invariants, concurrency), package layout,
  test class names.
- **Verification:** I read the plan against the brief's acceptance criteria and scope boundaries before
  implementing.

---

## Entry 1: Implementation from the plan
- **Prompt (verbatim, no sensitive data):** "Can you help me to create code files for attached
  assessment according to plan document and do changes if needed" (attached: assessment PDF and plan DOCX).
- **AI response (summary):** Generated the Maven project, domain/service/port/adapter code, the test suite
  and the docs, and ran what the sandbox allowed.
- **Decisions on suggestions**
  | Item | Decision | Reason |
  |---|---|---|
  | Core design: LinkedHashMap waitlist, one ReentrantLock, sealed results, skip-and-retain, reconcile-before-enroll | Accepted | Matches the brief; reviewed against invariants I1-I7 |
  | `Dropped.promoted` as `Optional<LearnerId>` (plan) | **Edited** to `List<LearnerId>` | A drop can fill several seats when skipped waiters became eligible since the last refresh |
  | `LearnerRecords.completedCourses` returning a set (plan) | **Edited** to `Optional<Set<>>` | Needed to distinguish an unknown learner (A10) from a known learner with no courses |
  | Unknown learner on drop | **Edited**: only reported when not enrolled, not waitlisted, and no record | Prevents an enrolled learner whose record vanished from being un-droppable |
  | `RosterSnapshot` / `WaitlistEntry` types | Added | Value equality makes invariant I7 directly testable |
  | I5 as a static invariant | **Edited**: checked only when records never lose courses | I5 is about the past; checkable only under monotonic records |
  | Maven Wrapper (`mvnw`) | **Rejected** (not generated) | Package registry unreachable in the AI sandbox; documented in README/LIMITATIONS |
- **Files informed:** everything under `src/` and `docs/`.
- **Verification evidence**
  1. `mvn clean verify` on my machine (JDK 22.0.2, Maven, compiled with `release 21`, 21 Nov 2026):
     - Before Entry 2: `Tests run: 179, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (12:05 IST).
     - After Entry 2 (adds `EdgeCaseTest`, 6 tests): `Tests run: 185, Failures: 0, Errors: 0, Skipped: 0`,
       `BUILD SUCCESS` (21:39 IST). 185 = 29 regular + 6 edge-case + 3 concurrency tests x 50 repetitions.
  2. Demo run (`mvn -q compile`, then `java -cp target/classes com.example.waitlist.Main`): enroll, waitlist,
     ineligible, unknown learner, drop with promotion and repeated drop outputs matched the documented policy.
  3. Earlier, in the AI sandbox (before real dependencies were available): all tests passed using stand-in
     test stubs, and a mutation check (lock removed from `enroll`) made all 3 concurrency tests fail
     (`NullPointerException`, `IllegalStateException: learner already present`,
     `NegativeArraySizeException`).
  4. Mutation check on my machine (21 Nov 2026): I commented out `lock.lock()` / `lock.unlock()` in
     `EnrollmentService.enroll()` and ran `mvn test -Dtest=ConcurrencyTest`.
     Result: `Tests run: 150, Failures: 29, Errors: 96`, `BUILD FAILURE` (125 of 150 runs failed).
     Symptoms: 6 learners enrolled in a capacity-5 course (I1 broken); waitlist of 56-58 entries instead of
     59 (lost updates); duplicate waitlist sequence numbers, e.g. two entries with `sequence=25` (I4 broken,
     because `nextSequence++` is not atomic); `IllegalStateException: learner already present`; and several
     `Enrolled` results for one duplicate learner. This shows the lock is what protects the invariants and
     that the concurrency tests detect its absence. I then restored the lock lines.
     Re-run after restoring the lock (22:02 IST): `mvn clean verify` -> `Tests run: 185, Failures: 0,
     Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

---

## Entry 2: Test-matrix comparison
- **Prompt (verbatim):** "can you add these also as I have pasted test results: 1. Test-matrix comparison
  (to do) 2. Code review (to do)". The AI was asked to produce the entries by doing the work, not by
  writing placeholders.
- **AI response (summary):** Listed the scenarios the acceptance criteria imply (capacity, duplicates,
  FIFO, prerequisites, drops, invariants, concurrency, failure of the records dependency) and compared them
  with `src/test`. Everything in the plan's matrix was already covered. Gaps found:
  | Gap | Decision | Reason |
  |---|---|---|
  | No test where one drop promotes **more than one** learner (the reason `promoted` became a list) | Accepted, added | Behaviour existed with no test |
  | `refreshWaitlist()` as a no-op when no seat is open or nobody is eligible | Accepted, added | Spec says it is safe to call any time |
  | Offering with an empty prerequisite set | Accepted, added | Boundary of the eligibility rule |
  | Positions shifting up after an earlier waiter leaves | Accepted, added | Position reporting was only tested indirectly |
  | Records dependency throwing during `enroll` and during `drop` promotion | Accepted, added | See Entry 3 |
  | Performance test with very large waitlists | **Rejected** | Out of scope for this bounded slice; O(k*w) is documented |
  | Case-insensitive learner ids | **Rejected** | Not in the brief; documented as a limitation |
- **Files informed:** `src/test/java/com/example/waitlist/EdgeCaseTest.java` (new; 6 tests at first, 7 after the Entry 4 fix),
  `docs/LIMITATIONS.md`.
- **Verification:** the new tests passed (38 of 38 test methods in the AI sandbox using stand-in test
  stubs). On my machine, `mvn clean verify` reported
  `Tests run: 185, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` (21 Nov 2026, 21:39 IST).

## Entry 3: Code review of `EnrollmentService` (and the roster it drives)
- **Prompt (same session as above):** review the class for races, off-by-one errors and state-corruption
  risks; verify each claim before reporting it.
- **AI claims and my decisions**
  | Claim | Decision | Verification |
  |---|---|---|
  | Position uses `waitlist.size()` after insert; possible off-by-one | **Rejected** (correct, 1-based) | Existing tests assert positions 1, 2, 3; new `positions_shiftUp...` test |
  | `enroll` can mutate state (reconcile) on a duplicate request, which looks like an I7 violation | **Accepted as documented**, no code change | Only happens if records changed since the last call; reconcile-first is the A5 design. Written in DESIGN section 5 |
  | A `LearnerRecords` exception inside `enroll` could corrupt state or leak the lock | **Rejected** as a bug (state is safe), test added | `enroll_whenRecordsFail...` checks unchanged snapshot and acquires the lock from another thread |
  | A records exception inside `drop` leaves the drop applied but the promotion not done | **Accepted** as a limitation, not fixed | Swallowing the exception would hide real failures. `drop_whenRecordsFailDuringPromotion...` proves state stays consistent and `refreshWaitlist()` recovers. Added to LIMITATIONS |
  | `ReentrantLock` allows re-entry, so a records adapter calling back into the service would modify the waitlist while it is iterated | **Accepted** as a documented adapter rule | Reasoned from the code; no such adapter exists here. Added to LIMITATIONS |
  | Records called under the lock (I/O under a lock) | **Accepted**, already documented | DESIGN section 7 |
  | Reads take the lock and return immutable copies, so no leak of internal collections | Confirmed, no change | `snapshot_isImmutableCopy_notALiveView` |
- **Files changed as a result:** `EdgeCaseTest.java` (new), `docs/LIMITATIONS.md`. No production code change
  was needed.
- **Limitation of this entry:** the AI reviewing this code is the same AI that wrote it, so the review is
  not independent. An independent review in a separate AI chat is recorded in Entry 4.

---

## Entry 4: Independent code review (separate AI chat)
- **Prompt (scrubbed):** "Check race conditions and off-by-one errors, then record each claim with
  Accepted, Edited, or Rejected" (with `EnrollmentService.java` pasted into a new, separate AI chat).
- **Scope limit stated by that AI:** only `EnrollmentService` was attached, not `EnrollmentRoster`, so its
  off-by-one verdicts were unverified.
- **AI claims and my decisions** (verdict column is the reviewing AI's; the last column is what I checked)
  | # | Claim | AI verdict | My decision | My verification |
  |---|---|---|---|---|
  | 1 | Roster state can be corrupted by concurrent calls | Rejected | **Agree** | All public methods hold the one lock; mutation check showed 125/150 concurrency runs fail without it |
  | 2 | Drop + promote is not atomic | Rejected | **Agree** | `drop` removes and promotes in one critical section |
  | 3 | Eligibility can change between check and action (TOCTOU on records) | Edited | **Agree** | Worst case is a stale eligibility decision, never a double-booked seat; `refreshWaitlist()` reconciles. Already in DESIGN/LIMITATIONS |
  | 4 | Calling records under the lock is risky; re-entry is silently allowed | Edited | **Agree** | Already documented in LIMITATIONS (I/O under lock, adapters must not call back in) |
  | 5 | `requireNonNull(offering)` in the 3-arg constructor is unreachable; a null offering gives a bare NPE | Accepted | **Accepted, fixed** | Reproduced first: new test failed with `Cannot invoke "Offering.prerequisites()" because "<parameter1>" is null`. Fixed by null-checking inside the `this(...)` call; test now passes |
  | 6 | `drop` misclassifies waitlisted learners as UnknownLearner | Rejected | **Agree** | `DropTest.drop_waitlistedOnlyLearner_returnsNotEnrolled_andStaysWaitlisted` |
  | 7 | `leaveWaitlist` should call `fillOpenSeats` | Rejected | **Agree** | Removing a waiter cannot open a seat |
  | 8 | `snapshot`/`positionOf` can be stale because they do not fill seats | Rejected | **Agree** | Consistent reads under the lock; staleness relative to records is handled by `refreshWaitlist()` |
  | 9 | Exception in records/policy mid-drop loses the Dropped result and promotions | Edited | **Agree, already covered** | Same finding as my Entry 3; `EdgeCaseTest.drop_whenRecordsFailDuringPromotion...` and LIMITATIONS |
  | 10 | Waitlisted learner calling `enroll` gets `AlreadyEnrolled` after a hidden promotion | Edited | **Agree, documented only** | Intentional: reconcile runs first (A5) and may promote them. Returning a distinct `Promoted` result is out of scope for this slice |
  | 11 | Possible off-by-one in position or capacity (unverified, roster not seen) | Edited (unverified) | **Checked myself, no defect** | Positions are 1-based (`FifoPromotionTest`, `EnrollmentCapacityTest`); skipped waiters count toward position (documented in LIMITATIONS); `isFull()` uses `>=`; capacity <= 0 is rejected in `Offering` and `EnrollmentRoster` (`EnrollmentCapacityTest.constructor_rejectsNonPositiveCapacity`) |
- **Net result:** one real defect (#5), fixed with a regression test. Everything else was confirmed sound or
  already documented.
- **Files changed as a result:** `EnrollmentService.java` (constructor null check), `EdgeCaseTest.java`
  (+1 test, now 7 tests).
- **Verification:** the new test failed before the fix and passed after. On my machine (22:13 IST),
  `mvn clean verify` -> `Tests run: 186, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
  (`EdgeCaseTest` now 7 tests).
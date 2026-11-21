# AI Prompt and Use Record

AI use in this project, in the order it happened:
0. Planning (assessment brief -> implementation plan)
1. Implementation from the plan
2. Test-matrix comparison
3. Code review

---

## Entry 0: Planning (before any implementation)
- **Input given to AI:** the assessment brief (`BRAHMANANDAM_GORANTLA_AI_Assessment.pdf`).
- **Prompt (scrubbed):** **[YOU]** paste the exact prompt(s) you used to get the implementation plan,
  e.g. "Create a Java implementation plan for the attached assessment ...". Replace any names or other
  personal data. If you used several prompts, list each in order.
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
  implementing. **[YOU]** note anything you checked or questioned yourself.

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
     `Tests run: 179, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
     (29 regular tests + 3 concurrency tests x 50 repetitions).
  2. Demo run (`mvn -q compile`, then `java -cp target/classes com.example.waitlist.Main`): enroll, waitlist,
     ineligible, unknown learner, drop with promotion and repeated drop outputs matched the documented policy.
  3. Earlier, in the AI sandbox (before real dependencies were available): all tests passed using stand-in
     test stubs, and a mutation check (lock removed from `enroll`) made all 3 concurrency tests fail
     (`NullPointerException`, `IllegalStateException: learner already present`,
     `NegativeArraySizeException`).
  4. **[YOU]** repeat the mutation check on your machine (comment out `lock.lock()`/`lock.unlock()` in
     `enroll`, run `mvn test -Dtest=ConcurrencyTest`, restore) and paste the failures here.

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
- **Files informed:** `src/test/java/com/example/waitlist/EdgeCaseTest.java` (new, 6 tests),
  `docs/LIMITATIONS.md`.
- **Verification:** the new tests passed (38 of 38 test methods in the AI sandbox using stand-in test
  stubs). **[YOU]** run `mvn clean verify` and paste the new total (expected: 179 + 6 = 185 tests, 0 failures).

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
- **Limitation of this entry (be candid in the interview):** the AI reviewing this code is the same AI that
  wrote it, so the review is not independent. **[YOU]** for a stronger record, paste `EnrollmentService`
  into a separate AI chat, write down that chat's claims, and record your own accept/reject decisions and
  checks as an extra entry.
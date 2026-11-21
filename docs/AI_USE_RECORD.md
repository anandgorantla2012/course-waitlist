# AI Prompt and Use Record

> **DRAFT: you must edit this before submitting.** The assessment requires an honest record of what you
> actually did. Entry 1 below was drafted from the work Claude did in this session; replace the prompt
> text with your real (scrubbed) prompts, and add the entries for any other AI use you actually perform.
> Do not keep entries for things you did not do.

## Entry 1: Implementation from the plan (design review and code generation)
- **Prompt (summary):** Attached the assessment brief and a Java implementation plan; asked for code files
  matching the plan, "and do changes if needed".
- **AI response (summary):** Generated the Maven project, domain/service/port/adapter code, test suite and docs.
- **Decisions on suggestions**
  | Item | Decision | Reason |
  |---|---|---|
  | Core design: LinkedHashMap waitlist, one ReentrantLock, sealed results, skip-and-retain, reconcile-before-enroll | Accepted | Matches the brief; reviewed against invariants I1-I7 |
  | `Dropped.promoted` as `Optional<LearnerId>` (plan) | **Edited** to `List<LearnerId>` | A drop can fill several seats when skipped waiters became eligible since the last refresh |
  | `LearnerRecords.completedCourses` returning a set (plan) | **Edited** to `Optional<Set<>>` | Needed to distinguish an unknown learner (A10) from a known learner with no courses |
  | Unknown learner on drop | **Edited**: only reported when not enrolled, not waitlisted, and no record | Prevents an enrolled learner whose record vanished from being un-droppable |
  | `RosterSnapshot` / `WaitlistEntry` types | Added | Value equality makes invariant I7 directly testable |
  | I5 as a static invariant | **Edited**: checked only when records never lose courses | I5 is about the past; checkable only under monotonic records |
  | Maven Wrapper (`mvnw`) | **Rejected** (not generated) | Package registry unreachable in the build sandbox; documented in README/LIMITATIONS |
- **Files informed:** everything under `src/` and `docs/`.
- **Verification evidence**
  1. All tests were executed in a sandbox that could not download JUnit/AssertJ, using minimal stand-in
     stubs for the test APIs and a reflective runner: 32 test methods, 0 failures
     (concurrency tests x50 each, randomized test 3 seeds x 10,000 steps).
  2. Mutation check: lock removed from `enroll` -> all 3 concurrency tests failed
     (`NullPointerException`, `IllegalStateException: learner already present`, `NegativeArraySizeException`).
  3. **TODO (you):** run `mvn clean verify` with the real JUnit/AssertJ and paste the result here.

## Entry 2 (TODO, do for real): test matrix comparison
Ask an AI for a test matrix for the acceptance criteria, compare with `src/test`, record what you added or dropped.

## Entry 3 (TODO, do for real): code review
Paste `EnrollmentService` (no secrets) and ask for races and off-by-ones. Verify each claim yourself and
record Accepted / Edited / Rejected with a reason for each.

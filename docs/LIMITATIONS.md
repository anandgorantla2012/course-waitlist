# Limitations and Follow-up Risks
- In-memory only; state is lost on restart.
- One process; the lock does not span JVMs.
- Skip-and-retain rescans from the head: O(k*w) per drop. Fine at classroom scale.
- Eligibility is a prerequisite subset check only (no grades, corequisites, holds, or time-limited credit).
- Waitlist position counts skipped (ineligible) waiters, so a reported position is not a guaranteed
  promotion order.
- Promotion produces no notification; callers learn of it only from `Dropped.promoted` or `refreshWaitlist()`.
- Changes to learner records are not observed automatically; call `refreshWaitlist()` (or any mutating
  call, which reconciles first).
- `LearnerRecords` is called while holding the lock (I/O under a lock in a real adapter).
- Concurrency tests are probabilistic: they showed the lock works and failed when it was removed, but
  cannot prove absence of races.
- Single offering only; multiple offerings would need a per-offering lock map and lock ordering.
- The Maven wrapper (`mvnw`) is not committed; generate it with `mvn -N wrapper:wrapper`.
- If `LearnerRecords` throws during promotion inside `drop`, the drop has already taken effect but the
  promotion has not: the caller sees an exception, the seat stays open, nothing is corrupted, and the next
  `enroll`/`drop`/`refreshWaitlist()` reconciles it (covered by `EdgeCaseTest`).
- `ReentrantLock` is re-entrant: a `LearnerRecords` implementation that calls back into the service from
  inside `completedCourses` would run while the waitlist is being iterated. Adapters must not do this.
- Learner ids are case-sensitive strings (`Alice` and `alice` are different learners).
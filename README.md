# Course Waitlist Service

Enrollment, drop and FIFO waitlist promotion for **one course offering**. Plain Java 21, no frameworks,
in-memory state. See [docs/DESIGN.md](docs/DESIGN.md), [docs/AI_USE_RECORD.md](docs/AI_USE_RECORD.md)
and [docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Requirements
- JDK 21 or newer (the build targets Java 21 via `maven.compiler.release`; verified on JDK 22)
- Maven 3.9+ (the Maven wrapper is not committed; add it once with
  `mvn -N wrapper:wrapper -Dmaven=3.9.9`, then use `./mvnw`)

## Run the tests
```bash
mvn clean verify                        # compiles and runs all tests (unit, randomized, concurrency)
mvn -Dtest=PrerequisiteTest test        # one class
mvn -Dtest='ConcurrencyTest' test       # 3 tests x 50 repetitions
```
Expected result: `Tests run: 187, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
(37 regular tests plus 3 concurrency tests repeated 50 times).

## Run the demo
```bash
mvn -q compile
java -cp target/classes com.example.waitlist.Main
```

## Usage
```java
var records  = new InMemoryLearnerRecords().put("alice", "MATH101").put("bob", "MATH101");
var offering = new Offering(CourseId.of("CS201"), 1, Set.of(CourseId.of("MATH101")));
var service  = new EnrollmentService(offering, records);

service.enroll(LearnerId.of("alice"));   // Enrolled
service.enroll(LearnerId.of("bob"));     // Waitlisted(position=1)
service.drop(LearnerId.of("alice"));     // Dropped(promoted=[bob])
```
Results are sealed types, so callers can use an exhaustive `switch`.

## Key assumptions (full list in docs/DESIGN.md)
- **Prerequisite rule (required behavior):** the offering has one prerequisite course (for example
  `MATH101`). A learner is eligible if every prerequisite is in their completed set. The set can hold
  several courses, in which case the learner needs all of them.
- **Ineligible at request time:** rejected (`Ineligible`); never enrolled or waitlisted.
- **Ineligible at the front of the queue at promotion time:** skipped and retained at their original
  position; the next eligible waiter is promoted. If they become eligible later they regain priority.
- **No queue-jumping:** free seats are filled from the waitlist before any new request is handled.
- **Duplicates:** a duplicate enroll or a repeated drop is a harmless no-op with a clear result.
- **Concurrency:** one lock per service; every operation, including drop plus promotion, is atomic.

## Additional feature: flexible prerequisites (either course, or both)
The required behavior is a single prerequisite course. As an extension, the eligibility rule can be swapped
without touching the service: pass an `AnyOfPrerequisitesPolicy` to the three-argument constructor. A learner
is then eligible if they completed **at least one** of the listed courses, so `MATH101` or `MATH102` or both.

```java
Set<CourseId> options = Set.of(CourseId.of("MATH101"), CourseId.of("MATH102"));
var records = new InMemoryLearnerRecords()
        .put("anand", "MATH101")             // only MATH101  -> eligible
        .put("brahma", "MATH102")            // only MATH102  -> eligible
        .put("saroj", "MATH101", "MATH102")  // both          -> eligible
        .put("siva", "PHYS100");             // neither       -> Ineligible[missing=[MATH101, MATH102]]
var service = new EnrollmentService(
        new Offering(CourseId.of("CS201"), 2, options), records,
        new AnyOfPrerequisitesPolicy(options));
```
- The default policy is unchanged (all listed prerequisites required), so existing behavior and tests are not affected.
- For an any-of offering, `Ineligible.missing` lists every option, because any one of them would have been enough.
- The same policy is used at request time and at promotion time, so skip-and-retain behaves identically.
- Not built: mixed rules such as "(A or B) and C", grade thresholds, or a configurable rule engine.

## Project layout
```
src/main/java/com/example/waitlist/
  domain/    roster aggregate, ids, offering, snapshot
  service/   EnrollmentService, EnrollResult, DropResult
  policy/    PrerequisiteEligibilityPolicy (default), AnyOfPrerequisitesPolicy (extension)
  port/      LearnerRecords (external dependency)
  adapter/   InMemoryLearnerRecords
src/test/java/com/example/waitlist/   tests and support helpers
docs/        DESIGN.md, AI_USE_RECORD.md, LIMITATIONS.md
```

## Test-to-criteria map
| Acceptance criterion | Where covered |
|---|---|
| AC1 capacity available | `EnrollmentCapacityTest` |
| AC2 full / duplicates | `EnrollmentCapacityTest`, `DuplicateRequestTest` |
| AC3 FIFO promotion | `FifoPromotionTest` |
| AC4 prerequisite policy | `PrerequisiteTest` |
| AC5 invalid / repeated drops | `DropTest`, `EdgeCaseTest` |
| AC6 randomized + concurrency | `InvariantRandomizedTest`, `ConcurrencyTest` |
| AC7 AI use record | `docs/AI_USE_RECORD.md` |
| Additional feature (any-of prerequisites) | `EdgeCaseTest.anyOfPolicy_eligibleWithEitherOrBoth_rejectedWithNeither` |

`EdgeCaseTest` adds multi-seat promotion, refresh no-op, empty prerequisites, shifting positions, failures of
the learner-records dependency, a null offering, and the any-of policy.
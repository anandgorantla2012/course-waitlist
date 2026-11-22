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
Expected result: `Tests run: 186, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
(36 regular tests plus 3 concurrency tests repeated 50 times).

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
- **Prerequisite rule:** a learner is eligible if every prerequisite course is in their completed set.
- **Ineligible at request time:** rejected (`Ineligible`); never enrolled or waitlisted.
- **Ineligible at the front of the queue at promotion time:** skipped and retained at their original
  position; the next eligible waiter is promoted. If they become eligible later they regain priority.
- **No queue-jumping:** free seats are filled from the waitlist before any new request is handled.
- **Duplicates:** a duplicate enroll or a repeated drop is a harmless no-op with a clear result.
- **Concurrency:** one lock per service; every operation, including drop plus promotion, is atomic.

## Project layout
```
src/main/java/com/example/waitlist/
  domain/    roster aggregate, ids, offering, snapshot
  service/   EnrollmentService, EnrollResult, DropResult
  policy/    prerequisite eligibility
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

`EdgeCaseTest` adds multi-seat promotion, refresh no-op, empty prerequisites, shifting positions, and
failures of the learner-records dependency.
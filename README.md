# Course Waitlist Service

Enrollment, drop and FIFO waitlist promotion for **one course offering**. Plain Java 21, no frameworks,
in-memory state. See [docs/DESIGN.md](docs/DESIGN.md), [docs/AI_USE_RECORD.md](docs/AI_USE_RECORD.md)
and [docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Requirements
- JDK 21
- Maven 3.9+ (or add the wrapper once with `mvn -N wrapper:wrapper -Dmaven=3.9.9`, then use `./mvnw`)

## Run the tests
```bash
mvn clean verify          # compiles and runs all tests (unit, randomized, concurrency)
mvn -Dtest=PrerequisiteTest test        # one class
mvn -Dtest='ConcurrencyTest' test       # 3 tests x 50 repetitions
```

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

## Test-to-criteria map
| Acceptance criterion | Test class |
|---|---|
| AC1 capacity available | `EnrollmentCapacityTest` |
| AC2 full / duplicates | `EnrollmentCapacityTest`, `DuplicateRequestTest` |
| AC3 FIFO promotion | `FifoPromotionTest` |
| AC4 prerequisite policy | `PrerequisiteTest` |
| AC5 invalid / repeated drops | `DropTest` |
| AC6 randomized + concurrency | `InvariantRandomizedTest`, `ConcurrencyTest` |

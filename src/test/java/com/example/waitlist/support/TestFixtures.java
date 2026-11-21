package com.example.waitlist.support;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.service.EnrollmentService;

import java.util.Set;

public final class TestFixtures {
    public static final CourseId MATH = CourseId.of("MATH101");

    private TestFixtures() {}

    public static LearnerId id(String name) {
        return LearnerId.of(name);
    }

    public static Offering offering(int capacity) {
        return new Offering(CourseId.of("CS201"), capacity, Set.of(MATH));
    }

    /** Records where every named learner has completed MATH101 (i.e. is eligible). */
    public static InMemoryLearnerRecords eligible(String... names) {
        InMemoryLearnerRecords records = new InMemoryLearnerRecords();
        for (String n : names) {
            records.put(n, "MATH101");
        }
        return records;
    }

    public static EnrollmentService service(int capacity, InMemoryLearnerRecords records) {
        return new EnrollmentService(offering(capacity), records);
    }
}

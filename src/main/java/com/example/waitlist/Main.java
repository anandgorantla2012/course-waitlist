package com.example.waitlist;

import java.util.Set;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.service.EnrollmentService;

/** Tiny demo: capacity 2, prerequisite MATH101. Run with: java -cp target/classes com.example.waitlist.Main */
public final class Main {
    public static void main(String[] args) {
        InMemoryLearnerRecords records = new InMemoryLearnerRecords()
                .put("anand", "MATH101").put("brahma", "MATH101").put("saroj", "MATH101")
                .put("abhishek", "MATH101").put("siva");
        EnrollmentService svc = new EnrollmentService(
                new Offering(CourseId.of("CS201"), 2, Set.of(CourseId.of("MATH101"))), records);

        for (String name : new String[] {"anand", "brahma", "saroj", "abhishek", "siva", "kiran", "abhishek", "brahma"}) {
            System.out.println("enroll " + name + " -> " + svc.enroll(LearnerId.of(name)));
        }
        System.out.println("drop anand -> " + svc.drop(LearnerId.of("anand")));
        System.out.println("drop anand -> " + svc.drop(LearnerId.of("anand")));
        System.out.println(svc.snapshot());
    }
}

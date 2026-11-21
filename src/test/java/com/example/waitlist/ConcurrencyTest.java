package com.example.waitlist;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import com.example.waitlist.support.Concurrent;
import com.example.waitlist.support.RosterInvariants;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC6 concurrency. These tests are probabilistic: they can show the lock works, not prove it.
 * Mutation check: remove lock.lock()/unlock() from EnrollmentService.enroll and these tests fail.
 */
class ConcurrencyTest {
    private static final int THREADS = 64;
    private static final int CAPACITY = 5;

    @RepeatedTest(50)
    void concurrentEnroll_64threads_capacity5_exactly5Enrolled_restWaitlisted_noDuplicates() throws Exception {
        String[] names = new String[THREADS];
        for (int i = 0; i < THREADS; i++) names[i] = "L" + i;
        InMemoryLearnerRecords records = eligible(names);
        Offering offering = offering(CAPACITY);
        EnrollmentService svc = new EnrollmentService(offering, records);

        List<Callable<EnrollResult>> tasks = new ArrayList<>();
        for (String n : names) tasks.add(() -> svc.enroll(id(n)));
        List<EnrollResult> results = Concurrent.runAll(tasks);

        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).hasSize(CAPACITY);
        assertThat(s.waitlist()).hasSize(THREADS - CAPACITY);
        assertThat(results.stream().filter(r -> r instanceof EnrollResult.Enrolled).count()).isEqualTo(CAPACITY);
        // each waitlist position handed out exactly once
        List<Integer> positions = results.stream()
                .filter(r -> r instanceof EnrollResult.Waitlisted)
                .map(r -> ((EnrollResult.Waitlisted) r).position()).sorted().toList();
        assertThat(positions).hasSize(THREADS - CAPACITY);
        for (int i = 0; i < positions.size(); i++) assertThat(positions.get(i)).isEqualTo(i + 1);
        RosterInvariants.assertAll(s, records, offering);
    }

    @RepeatedTest(50)
    void concurrentDuplicateEnroll_sameLearner_createsOneRecord() throws Exception {
        InMemoryLearnerRecords records = eligible("only");
        EnrollmentService svc = service(1, records);
        List<Callable<EnrollResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) tasks.add(() -> svc.enroll(id("only")));
        List<EnrollResult> results = Concurrent.runAll(tasks);

        assertThat(results.stream().filter(r -> r instanceof EnrollResult.Enrolled).count()).isEqualTo(1);
        assertThat(svc.snapshot().enrolled()).containsExactly(id("only"));
        assertThat(svc.snapshot().waitlist()).isEmpty();
    }

    @RepeatedTest(50)
    void concurrentMixedEnrollAndDrop_preservesInvariants_andConservesLearners() throws Exception {
        int learners = 40;
        String[] names = new String[learners];
        for (int i = 0; i < learners; i++) names[i] = "L" + i;
        InMemoryLearnerRecords records = eligible(names);
        Offering offering = offering(CAPACITY);
        EnrollmentService svc = new EnrollmentService(offering, records);

        // each learner: enroll, then try to drop (drop is a no-op if they are still waitlisted)
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (String n : names) {
            tasks.add(() -> {
                svc.enroll(id(n));
                return svc.drop(id(n)) instanceof DropResult.Dropped;
            });
        }
        long dropped = Concurrent.runAll(tasks).stream().filter(b -> b).count();

        RosterSnapshot s = svc.snapshot();
        // every learner entered once and only leaves via a successful drop
        assertThat(s.enrolled().size() + s.waitlist().size()).isEqualTo(learners - (int) dropped);
        Collection<LearnerId> all = new HashSet<>(s.enrolled());
        all.addAll(s.waitlistLearners());
        assertThat(all).hasSize(s.enrolled().size() + s.waitlist().size()); // disjoint, no duplicates
        RosterInvariants.assertAll(s, records, offering);
    }
}

package com.example.waitlist;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.policy.AnyOfPrerequisitesPolicy;
import com.example.waitlist.port.LearnerRecords;
import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import com.example.waitlist.support.Concurrent;
import static com.example.waitlist.support.TestFixtures.eligible;
import static com.example.waitlist.support.TestFixtures.id;
import static com.example.waitlist.support.TestFixtures.offering;
import static com.example.waitlist.support.TestFixtures.service;

/** Gaps found in the test-matrix comparison and code review (see docs/AI_USE_RECORD.md, Entries 2 and 3). */
class EdgeCaseTest {

    @Test
    void drop_canPromoteMoreThanOneWaiter_whenSkippedWaitersBecameEligible() {
        InMemoryLearnerRecords r = eligible("a", "b", "c", "d");
        EnrollmentService svc = service(2, r);
        for (String n : new String[] {"a", "b", "c", "d"}) {
            svc.enroll(id(n)); // a, b enrolled; c, d waiting
        }
        r.put("c");
        r.put("d");          // both lose eligibility
        svc.drop(id("a"));   // c and d skipped, retained; seat stays open
        r.put("c", "MATH101");
        r.put("d", "MATH101");

        assertThat(svc.drop(id("b")))
                .isEqualTo(new DropResult.Dropped(id("b"), List.of(id("c"), id("d"))));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("c"), id("d"));
        assertThat(svc.snapshot().waitlist()).isEmpty();
    }

    @Test
    void refreshWaitlist_whenNoSeatOpen_orNobodyEligible_isNoOp() {
        InMemoryLearnerRecords r = eligible("a", "b");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.refreshWaitlist()).isEmpty();
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void enroll_offeringWithNoPrerequisites_everyKnownLearnerIsEligible() {
        InMemoryLearnerRecords r = new InMemoryLearnerRecords().put("a");
        var svc = new EnrollmentService(new Offering(CourseId.of("OPEN101"), 1, Set.of()), r);
        assertThat(svc.enroll(id("a"))).isInstanceOf(EnrollResult.Enrolled.class);
    }

    @Test
    void positions_shiftUp_whenAnEarlierWaiterLeaves() {
        EnrollmentService svc = service(1, eligible("a", "b", "c", "d"));
        for (String n : new String[] {"a", "b", "c", "d"}) {
            svc.enroll(id(n));
        }
        svc.leaveWaitlist(id("b"));
        assertThat(svc.positionOf(id("c"))).contains(1);
        assertThat(svc.positionOf(id("d"))).contains(2);
        assertThat(svc.enroll(id("d"))).isEqualTo(new EnrollResult.AlreadyWaitlisted(id("d"), 2));
    }

    @Test
    void enroll_whenRecordsFail_throws_leavesStateUnchanged_andReleasesTheLock() throws Exception {
        InMemoryLearnerRecords real = eligible("a", "b");
        AtomicBoolean fail = new AtomicBoolean(false);
        LearnerRecords flaky = l -> {
            if (fail.get()) {
                throw new IllegalStateException("records down");
            }
            return real.completedCourses(l);
        };
        EnrollmentService svc = new EnrollmentService(offering(1), flaky);
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        RosterSnapshot before = svc.snapshot();

        fail.set(true);
        assertThatThrownBy(() -> svc.enroll(id("c"))).isInstanceOf(IllegalStateException.class);
        assertThat(svc.snapshot()).isEqualTo(before);

        // ReentrantLock would hide a leaked lock on the same thread, so check from another thread
        List<Callable<RosterSnapshot>> tasks = List.of(svc::snapshot);
        assertThat(Concurrent.runAll(tasks).get(0)).isEqualTo(before);
    }

    @Test
    void drop_whenRecordsFailDuringPromotion_stateStaysConsistent_andRefreshRecovers() {
        InMemoryLearnerRecords real = eligible("a", "b");
        AtomicBoolean fail = new AtomicBoolean(false);
        LearnerRecords flaky = l -> {
            if (fail.get()) {
                throw new IllegalStateException("records down");
            }
            return real.completedCourses(l);
        };
        EnrollmentService svc = new EnrollmentService(offering(1), flaky);
        svc.enroll(id("a"));
        svc.enroll(id("b")); // b waiting

        fail.set(true);
        assertThatThrownBy(() -> svc.drop(id("a"))).isInstanceOf(IllegalStateException.class);
        // Known limitation: the drop took effect, the promotion did not. Nothing is corrupted:
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).isEmpty();
        assertThat(s.waitlistLearners()).containsExactly(id("b"));

        fail.set(false);
        assertThat(svc.refreshWaitlist()).containsExactly(id("b")); // seat is reconciled later
        assertThat(svc.snapshot().enrolled()).containsExactly(id("b"));
    }

    @Test
    void constructor_nullOffering_failsWithClearMessage() {
        String message = null;
        try {
            new EnrollmentService(null, eligible());
        } catch (NullPointerException e) {
            message = e.getMessage();
        }
        assertThat(message).contains("offering must not be null");
    }

    @Test
    void anyOfPolicy_eligibleWithEitherOrBoth_rejectedWithNeither() {
        Set<CourseId> options = Set.of(CourseId.of("MATH101"), CourseId.of("MATH102"));
        InMemoryLearnerRecords r = new InMemoryLearnerRecords()
                .put("a", "MATH101").put("b", "MATH102")
                .put("c", "MATH101", "MATH102").put("d", "PHYS100");
        var svc = new EnrollmentService(new Offering(CourseId.of("CS201"), 4, options), r,
                new AnyOfPrerequisitesPolicy(options));
        assertThat(svc.enroll(id("a"))).isInstanceOf(EnrollResult.Enrolled.class);
        assertThat(svc.enroll(id("b"))).isInstanceOf(EnrollResult.Enrolled.class);
        assertThat(svc.enroll(id("c"))).isInstanceOf(EnrollResult.Enrolled.class);
        assertThat(svc.enroll(id("d"))).isInstanceOf(EnrollResult.Ineligible.class);
    }
}
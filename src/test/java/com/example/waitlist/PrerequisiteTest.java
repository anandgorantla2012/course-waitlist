package com.example.waitlist;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** AC4: ineligible learners are never enrolled or promoted; skip-and-retain (A3, A4, A5). */
class PrerequisiteTest {

    @Test
    void enroll_ineligible_withSeatsOpen_isRejected_andNotStored() {
        InMemoryLearnerRecords r = eligible().put("pat"); // no completed courses
        EnrollmentService svc = service(2, r);
        assertThat(svc.enroll(id("pat"))).isEqualTo(new EnrollResult.Ineligible(id("pat"), Set.of(MATH)));
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).isEmpty();
        assertThat(s.waitlist()).isEmpty();
    }

    @Test
    void enroll_ineligible_whenFull_isRejected_andNeverWaitlisted() {
        InMemoryLearnerRecords r = eligible("a").put("pat");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.enroll(id("pat"))).isInstanceOf(EnrollResult.Ineligible.class);
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void enroll_reportsOnlyTheMissingPrerequisites() {
        var offering = new com.example.waitlist.domain.Offering(
                CourseId.of("CS301"), 2, Set.of(CourseId.of("A"), CourseId.of("B")));
        InMemoryLearnerRecords r = new InMemoryLearnerRecords().put("pat", "A");
        var svc = new EnrollmentService(offering, r);
        assertThat(svc.enroll(id("pat")))
                .isEqualTo(new EnrollResult.Ineligible(id("pat"), Set.of(CourseId.of("B"))));
    }

    @Test
    void enroll_unknownLearner_returnsUnknownLearner_noStateChange() {
        EnrollmentService svc = service(1, eligible("a"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.enroll(id("ghost"))).isEqualTo(new EnrollResult.UnknownLearner(id("ghost")));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void promotion_ineligibleWaiterAtFront_isSkipped_retainedInPlace_nextEligiblePromoted() {
        InMemoryLearnerRecords r = eligible("a", "b", "c");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        svc.enroll(id("b")); // position 1
        svc.enroll(id("c")); // position 2
        r.remove(id("b"));   // b's record becomes unavailable -> ineligible at promotion time
        r.put("b");          // known learner, but no completed courses

        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of(id("c"))));
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).containsExactly(id("c"));
        assertThat(s.waitlistLearners()).containsExactly(id("b")); // retained, not removed
        assertThat(svc.positionOf(id("b"))).contains(1);
    }

    @Test
    void promotion_allWaitersIneligible_leavesSeatOpen() {
        InMemoryLearnerRecords r = eligible("a", "b", "c");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        svc.enroll(id("c"));
        r.put("b");
        r.put("c");

        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of()));
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).isEmpty();
        assertThat(s.waitlistLearners()).containsExactly(id("b"), id("c"));
    }

    @Test
    void refresh_skippedLearnerBecomesEligible_regainsOriginalPriority() {
        InMemoryLearnerRecords r = eligible("a", "b", "c");
        EnrollmentService svc = service(2, r);
        // fill with a, c; b waits first, then d
        r.put("d", "MATH101");
        svc.enroll(id("a"));
        svc.enroll(id("c"));
        svc.enroll(id("b")); // waitlist position 1
        svc.enroll(id("d")); // waitlist position 2
        r.put("b");          // b loses eligibility (record correction)
        svc.drop(id("a"));   // seat opens; b skipped, d promoted
        assertThat(svc.snapshot().enrolled()).containsExactly(id("c"), id("d"));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("b"));

        r.put("b", "MATH101"); // b becomes eligible again
        svc.drop(id("c"));     // seat opens; b is now promoted
        assertThat(svc.snapshot().enrolled()).containsExactly(id("d"), id("b"));
    }

    @Test
    void refreshWaitlist_afterRecordsUpdate_promotesIntoOpenSeat() {
        InMemoryLearnerRecords r = eligible("a", "b");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        r.put("b");
        svc.drop(id("a")); // b skipped, seat stays open
        assertThat(svc.snapshot().enrolled()).isEmpty();

        r.put("b", "MATH101");
        assertThat(svc.refreshWaitlist()).containsExactly(id("b"));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("b"));
        assertThat(svc.snapshot().waitlist()).isEmpty();
    }

    @Test
    void enroll_noQueueJumping_eligibleWaiterGetsFreedSeatBeforeNewcomer() {
        InMemoryLearnerRecords r = eligible("a", "b", "late");
        EnrollmentService svc = service(1, r);
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        r.put("b");            // b ineligible while seat frees up -> seat stays open
        svc.drop(id("a"));
        r.put("b", "MATH101"); // b eligible again, but nobody has called refresh yet

        // newcomer arrives; A5 reconciles first so b (earlier in line) gets the seat
        assertThat(svc.enroll(id("late"))).isEqualTo(new EnrollResult.Waitlisted(id("late"), 1));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("b"));
    }
}

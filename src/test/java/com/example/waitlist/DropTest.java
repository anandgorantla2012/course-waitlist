package com.example.waitlist;

import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC5: invalid and repeated drops return clear results and leave state untouched (A8, A10, I7). */
class DropTest {

    @Test
    void drop_unknownLearner_returnsUnknownLearner_noStateChange() {
        EnrollmentService svc = service(1, eligible("a"));
        svc.enroll(id("a"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.drop(id("ghost"))).isEqualTo(new DropResult.UnknownLearner(id("ghost")));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void drop_knownButNeverEnrolled_returnsNotEnrolled_noStateChange() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        svc.enroll(id("a"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.drop(id("b"))).isEqualTo(new DropResult.NotEnrolled(id("b")));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void drop_waitlistedOnlyLearner_returnsNotEnrolled_andStaysWaitlisted() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.drop(id("b"))).isEqualTo(new DropResult.NotEnrolled(id("b")));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void drop_twice_secondIsNoOp_andDoesNotFreeASecondSeat() {
        EnrollmentService svc = service(1, eligible("a", "b", "c"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        svc.enroll(id("c"));

        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of(id("b"))));
        RosterSnapshot afterFirst = svc.snapshot();
        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.NotEnrolled(id("a")));
        assertThat(svc.snapshot()).isEqualTo(afterFirst);
        assertThat(afterFirst.enrolled()).containsExactly(id("b")); // c was NOT promoted
    }

    @Test
    void droppedLearner_canReEnroll_andJoinsBackOfQueueIfFull() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        svc.drop(id("a")); // b promoted
        assertThat(svc.enroll(id("a")).toString()).contains("Waitlisted").contains("position=1");
    }

    @Test
    void nullArguments_throwNullPointerException() {
        EnrollmentService svc = service(1, eligible("a"));
        assertThatThrownBy(() -> svc.enroll(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> svc.drop(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> svc.leaveWaitlist(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> svc.positionOf(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com.example.waitlist.domain.LearnerId.of(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com.example.waitlist.domain.LearnerId.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshot_isImmutableCopy_notALiveView() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        svc.enroll(id("a"));
        RosterSnapshot s = svc.snapshot();
        svc.enroll(id("b"));
        assertThat(s.waitlist()).isEmpty(); // old snapshot unchanged
        assertThatThrownBy(() -> s.enrolled().add(id("x"))).isInstanceOf(UnsupportedOperationException.class);
    }
}

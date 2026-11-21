package com.example.waitlist;

import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC1 (and the capacity half of AC2). */
class EnrollmentCapacityTest {

    @Test
    void enroll_whenSeatsOpen_andEligible_isEnrolled() {
        EnrollmentService svc = service(3, eligible("a"));
        assertThat(svc.enroll(id("a"))).isEqualTo(new EnrollResult.Enrolled(id("a")));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("a"));
    }

    @Test
    void enroll_uptoExactlyCapacity_allEnrolled_noWaitlist() {
        EnrollmentService svc = service(3, eligible("a", "b", "c"));
        for (String n : new String[] {"a", "b", "c"}) {
            assertThat(svc.enroll(id(n))).isInstanceOf(EnrollResult.Enrolled.class);
        }
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).hasSize(3);
        assertThat(s.waitlist()).isEmpty();
    }

    @Test
    void enroll_capacityOneCourse_firstEnrolled_secondWaitlistedAtPosition1() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        assertThat(svc.enroll(id("a"))).isInstanceOf(EnrollResult.Enrolled.class);
        assertThat(svc.enroll(id("b"))).isEqualTo(new EnrollResult.Waitlisted(id("b"), 1));
    }

    @Test
    void enroll_whenFull_andEligible_joinsWaitlistAtPositionN() {
        EnrollmentService svc = service(1, eligible("a", "b", "c", "d"));
        svc.enroll(id("a"));
        assertThat(svc.enroll(id("b"))).isEqualTo(new EnrollResult.Waitlisted(id("b"), 1));
        assertThat(svc.enroll(id("c"))).isEqualTo(new EnrollResult.Waitlisted(id("c"), 2));
        assertThat(svc.enroll(id("d"))).isEqualTo(new EnrollResult.Waitlisted(id("d"), 3));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("a"));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("b"), id("c"), id("d"));
    }

    @Test
    void constructor_rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> offering(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offering(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.example.waitlist;

import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** AC2: duplicate enrollment requests never create duplicate records (A7). */
class DuplicateRequestTest {

    @Test
    void enroll_duplicateWhileEnrolled_returnsAlreadyEnrolled_noStateChange() {
        EnrollmentService svc = service(2, eligible("a"));
        svc.enroll(id("a"));
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.enroll(id("a"))).isEqualTo(new EnrollResult.AlreadyEnrolled(id("a")));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void enroll_duplicateWhileWaitlisted_returnsSamePosition_noStateChange() {
        EnrollmentService svc = service(1, eligible("a", "b", "c"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        svc.enroll(id("c")); // c at position 2
        RosterSnapshot before = svc.snapshot();
        assertThat(svc.enroll(id("c"))).isEqualTo(new EnrollResult.AlreadyWaitlisted(id("c"), 2));
        assertThat(svc.snapshot()).isEqualTo(before);
    }

    @Test
    void enroll_100RepeatedRequests_leaveExactlyOneRecord() {
        EnrollmentService svc = service(1, eligible("a", "b"));
        svc.enroll(id("a"));
        for (int i = 0; i < 100; i++) {
            svc.enroll(id("b"));
            svc.enroll(id("a"));
        }
        RosterSnapshot s = svc.snapshot();
        assertThat(s.enrolled()).containsExactly(id("a"));
        assertThat(s.waitlistLearners()).containsExactly(id("b"));
        assertThat(s.waitlist().get(0).sequence()).isEqualTo(1L); // never re-queued
    }
}

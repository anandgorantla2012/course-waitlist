package com.example.waitlist;

import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** AC3: dropping promotes the earliest eligible waiter. */
class FifoPromotionTest {

    @Test
    void drop_promotesEarliestWaiter() {
        EnrollmentService svc = service(1, eligible("a", "b", "c"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        svc.enroll(id("c"));

        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of(id("b"))));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("b"));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("c"));
    }

    @Test
    void drop_sequentialDrops_promoteAThenBThenC_andPreserveRemainderOrder() {
        EnrollmentService svc = service(1, eligible("x", "a", "b", "c", "d"));
        for (String n : new String[] {"x", "a", "b", "c", "d"}) {
            svc.enroll(id(n));
        }
        assertThat(svc.drop(id("x"))).isEqualTo(new DropResult.Dropped(id("x"), List.of(id("a"))));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("b"), id("c"), id("d"));
        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of(id("b"))));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("c"), id("d"));
        assertThat(svc.drop(id("b"))).isEqualTo(new DropResult.Dropped(id("b"), List.of(id("c"))));
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("d"));
    }

    @Test
    void drop_withEmptyWaitlist_opensSeat_andPromotesNobody() {
        EnrollmentService svc = service(2, eligible("a", "b"));
        svc.enroll(id("a"));
        svc.enroll(id("b"));
        assertThat(svc.drop(id("a"))).isEqualTo(new DropResult.Dropped(id("a"), List.of()));
        assertThat(svc.snapshot().enrolled()).containsExactly(id("b"));
    }

    @Test
    void leaveWaitlist_removesWaiter_andKeepsOthersInOrder() {
        EnrollmentService svc = service(1, eligible("a", "b", "c", "d"));
        for (String n : new String[] {"a", "b", "c", "d"}) {
            svc.enroll(id(n));
        }
        assertThat(svc.leaveWaitlist(id("c"))).isTrue();
        assertThat(svc.leaveWaitlist(id("c"))).isFalse(); // repeated: harmless
        assertThat(svc.leaveWaitlist(id("a"))).isFalse(); // enrolled, not waiting
        assertThat(svc.snapshot().waitlistLearners()).containsExactly(id("b"), id("d"));
    }
}

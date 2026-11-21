package com.example.waitlist.support;

import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.domain.WaitlistEntry;
import com.example.waitlist.policy.PrerequisiteEligibilityPolicy;
import com.example.waitlist.port.LearnerRecords;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Invariants I1-I6 from docs/DESIGN.md. (I7 is asserted by comparing before/after snapshots in tests.) */
public final class RosterInvariants {
    private RosterInvariants() {}

    public static void assertAll(RosterSnapshot s, LearnerRecords records, Offering offering) {
        // I1
        assertThat(s.enrolled().size()).as("I1 enrolled <= capacity").isLessThanOrEqualTo(s.capacity());
        assertThat(s.capacity()).isEqualTo(offering.capacity());
        // I2
        Set<LearnerId> enrolled = new HashSet<>(s.enrolled());
        assertThat(enrolled).as("I3-ish: enrolled has no duplicates").hasSize(s.enrolled().size());
        List<LearnerId> waiting = s.waitlistLearners();
        assertThat(new HashSet<>(waiting)).as("I3 waitlist has no duplicates").hasSize(waiting.size());
        assertThat(waiting).as("I2 enrolled and waitlist are disjoint").noneMatch(enrolled::contains);
        // I4
        long previous = Long.MIN_VALUE;
        for (WaitlistEntry e : s.waitlist()) {
            assertThat(e.sequence()).as("I4 sequence strictly increasing").isGreaterThan(previous);
            previous = e.sequence();
        }
        // I6 (work-conserving). Holds whenever records have not changed since the last service call
        // (or after refreshWaitlist()).
        if (s.enrolled().size() < s.capacity()) {
            var policy = new PrerequisiteEligibilityPolicy(offering.prerequisites());
            for (LearnerId w : waiting) {
                boolean eligibleNow = records.completedCourses(w).map(policy::isEligible).orElse(false);
                assertThat(eligibleNow).as("I6 free seat but eligible waiter " + w).isFalse();
            }
        }
    }

    /**
     * I5 (everyone enrolled was eligible when enrolled). Only checkable statically when records
     * never lose courses: then "eligible at enrollment" implies "eligible now".
     */
    public static void assertEnrolledAreEligible(RosterSnapshot s, LearnerRecords records, Offering offering) {
        var policy = new PrerequisiteEligibilityPolicy(offering.prerequisites());
        for (LearnerId e : s.enrolled()) {
            assertThat(records.completedCourses(e).map(policy::isEligible).orElse(false))
                    .as("I5 enrolled learner " + e + " must be eligible").isTrue();
        }
    }
}

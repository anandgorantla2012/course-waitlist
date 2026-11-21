package com.example.waitlist;

import com.example.waitlist.adapter.InMemoryLearnerRecords;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.service.DropResult;
import com.example.waitlist.service.EnrollResult;
import com.example.waitlist.service.EnrollmentService;
import com.example.waitlist.support.RosterInvariants;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.example.waitlist.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** AC6: seeded randomized test, invariants checked after every step. The seed is printed on failure. */
class InvariantRandomizedTest {
    private static final int LEARNERS = 20;
    private static final int OPERATIONS = 10_000;
    private static final long[] SEEDS = {1L, 42L, 2026L};

    @Test
    void randomOperations_neverBreakInvariants() {
        for (long seed : SEEDS) {
            runWithSeed(seed);
        }
    }

    private void runWithSeed(long seed) {
        Random rnd = new Random(seed);
        Offering offering = offering(5);
        InMemoryLearnerRecords records = new InMemoryLearnerRecords();
        for (int i = 0; i < LEARNERS; i++) {
            // ~70% eligible, ~25% known but ineligible, ~5% unknown (no record)
            double d = rnd.nextDouble();
            if (d < 0.70) records.put("L" + i, "MATH101");
            else if (d < 0.95) records.put("L" + i);
        }
        EnrollmentService svc = new EnrollmentService(offering, records);

        for (int step = 0; step < OPERATIONS; step++) {
            LearnerId who = id("L" + rnd.nextInt(LEARNERS));
            RosterSnapshot before = svc.snapshot();
            String op;
            try {
                int pick = rnd.nextInt(10);
                if (pick < 4) {
                    op = "enroll " + who;
                    EnrollResult r = svc.enroll(who);
                    if (!(r instanceof EnrollResult.Enrolled) && !(r instanceof EnrollResult.Waitlisted)) {
                        assertThat(svc.snapshot()).as("I7 after " + op + " -> " + r).isEqualTo(before);
                    }
                } else if (pick < 7) {
                    op = "drop " + who;
                    DropResult r = svc.drop(who);
                    if (!(r instanceof DropResult.Dropped)) {
                        assertThat(svc.snapshot()).as("I7 after " + op + " -> " + r).isEqualTo(before);
                    }
                } else if (pick < 8) {
                    op = "leaveWaitlist " + who;
                    svc.leaveWaitlist(who);
                } else {
                    // records only ever GAIN courses (keeps I5 checkable), then refresh
                    op = "grant+refresh " + who;
                    records.complete(who, MATH);
                    svc.refreshWaitlist();
                }
                RosterSnapshot after = svc.snapshot();
                RosterInvariants.assertAll(after, records, offering);
                RosterInvariants.assertEnrolledAreEligible(after, records, offering);
            } catch (Throwable t) {
                throw new AssertionError("seed=" + seed + " step=" + step + ": " + t.getMessage(), t);
            }
        }
    }
}

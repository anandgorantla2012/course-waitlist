package com.example.waitlist.domain;

import java.util.List;

/**
 * Immutable point-in-time copy of the roster. Value-equal snapshots mean identical state, which is
 * how tests assert invariant I7 ("invalid or repeated operations change nothing").
 */
public record RosterSnapshot(int capacity, List<LearnerId> enrolled, List<WaitlistEntry> waitlist) {
    public RosterSnapshot {
        enrolled = List.copyOf(enrolled);
        waitlist = List.copyOf(waitlist);
    }

    public List<LearnerId> waitlistLearners() {
        return waitlist.stream().map(WaitlistEntry::learner).toList();
    }
}

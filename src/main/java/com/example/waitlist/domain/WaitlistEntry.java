package com.example.waitlist.domain;

import java.util.Objects;

/** A waiting learner plus a monotonically increasing sequence number (FIFO tie-break and audit field). */
public record WaitlistEntry(LearnerId learner, long sequence) {
    public WaitlistEntry {
        Objects.requireNonNull(learner, "learner must not be null");
    }
}

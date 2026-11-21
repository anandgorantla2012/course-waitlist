package com.example.waitlist.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The aggregate: who is enrolled and who is waiting. Pure state and structural rules, no locking
 * (the service owns atomicity) and no eligibility knowledge (the caller passes a predicate).
 *
 * <p>The waitlist is a single {@link LinkedHashMap} so that "no duplicates" and "FIFO" are structural:
 * O(1) membership, O(1) removal from the middle, iteration in insertion order. A learner is in at most
 * one of the two collections (A6); the mutators below enforce that.
 */
public final class EnrollmentRoster {
    private final int capacity;
    private final Set<LearnerId> enrolled = new LinkedHashSet<>();
    private final Map<LearnerId, WaitlistEntry> waitlist = new LinkedHashMap<>();
    private long nextSequence = 1;

    public EnrollmentRoster(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public boolean isFull() {
        return enrolled.size() >= capacity;
    }

    public int openSeats() {
        return capacity - enrolled.size();
    }

    public boolean isEnrolled(LearnerId id) {
        return enrolled.contains(id);
    }

    public boolean isWaitlisted(LearnerId id) {
        return waitlist.containsKey(id);
    }

    /** 1-based position among everyone currently waiting (skipped learners keep their place), or empty. */
    public Optional<Integer> positionOf(LearnerId id) {
        if (!waitlist.containsKey(id)) {
            return Optional.empty();
        }
        int position = 1;
        for (LearnerId waiting : waitlist.keySet()) {
            if (waiting.equals(id)) {
                return Optional.of(position);
            }
            position++;
        }
        throw new IllegalStateException("unreachable: key present but not iterated");
    }

    /** Adds to the enrolled set. Caller must have checked capacity and that the learner is in neither collection. */
    public void enroll(LearnerId id) {
        if (isFull()) {
            throw new IllegalStateException("roster is full");
        }
        if (isWaitlisted(id) || !enrolled.add(id)) {
            throw new IllegalStateException("learner already present: " + id);
        }
    }

    /** Appends to the back of the waitlist and returns the 1-based position. */
    public int addToWaitlist(LearnerId id) {
        if (isEnrolled(id) || waitlist.containsKey(id)) {
            throw new IllegalStateException("learner already present: " + id);
        }
        waitlist.put(id, new WaitlistEntry(id, nextSequence++));
        return waitlist.size();
    }

    public boolean removeEnrolled(LearnerId id) {
        return enrolled.remove(id);
    }

    public boolean removeFromWaitlist(LearnerId id) {
        return waitlist.remove(id) != null;
    }

    /**
     * Promotes the earliest waitlisted learner satisfying {@code eligibleNow}, if a seat is open.
     * Ineligible waiters ahead of the promoted learner are skipped and RETAINED in place (A4).
     * Always rescans from the head, because eligibility is dynamic.
     *
     * @return the promoted learner, or empty if no seat is open or nobody waiting is eligible
     */
    public Optional<LearnerId> promoteNextEligible(Predicate<LearnerId> eligibleNow) {
        if (isFull()) {
            return Optional.empty();
        }
        for (WaitlistEntry entry : waitlist.values()) {
            if (eligibleNow.test(entry.learner())) {
                waitlist.remove(entry.learner());
                enrolled.add(entry.learner());
                return Optional.of(entry.learner());
            }
        }
        return Optional.empty();
    }

    /** Promotes repeatedly until the roster is full or no eligible waiter remains. O(k*w) for k open seats. */
    public List<LearnerId> fillOpenSeats(Predicate<LearnerId> eligibleNow) {
        List<LearnerId> promoted = new ArrayList<>();
        Optional<LearnerId> next;
        while ((next = promoteNextEligible(eligibleNow)).isPresent()) {
            promoted.add(next.get());
        }
        return promoted;
    }

    public RosterSnapshot snapshot() {
        return new RosterSnapshot(capacity, List.copyOf(enrolled), List.copyOf(waitlist.values()));
    }
}

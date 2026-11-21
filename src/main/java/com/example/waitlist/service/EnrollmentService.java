package com.example.waitlist.service;

import com.example.waitlist.domain.EnrollmentRoster;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.domain.Offering;
import com.example.waitlist.domain.RosterSnapshot;
import com.example.waitlist.policy.EligibilityPolicy;
import com.example.waitlist.policy.PrerequisiteEligibilityPolicy;
import com.example.waitlist.port.LearnerRecords;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.example.waitlist.domain.CourseId;

/**
 * Public API for one offering. Owns the single lock: every public method (reads included) holds it, so
 * each operation, including "drop + promote", is atomic.
 *
 * <p>Policy summary (see docs/DESIGN.md): ineligible at request time -> rejected; ineligible at the
 * front of the queue at promotion time -> skipped and retained in place; free seats are always filled
 * from the waitlist before a new request is considered (no queue-jumping).
 */
public final class EnrollmentService {
    private final EnrollmentRoster roster;
    private final EligibilityPolicy policy;
    private final LearnerRecords records;
    private final ReentrantLock lock = new ReentrantLock();

    public EnrollmentService(Offering offering, LearnerRecords records) {
        this(offering, records, new PrerequisiteEligibilityPolicy(offering.prerequisites()));
    }

    public EnrollmentService(Offering offering, LearnerRecords records, EligibilityPolicy policy) {
        Objects.requireNonNull(offering, "offering must not be null");
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.roster = new EnrollmentRoster(offering.capacity());
    }

    public EnrollResult enroll(LearnerId id) {
        Objects.requireNonNull(id, "learner id must not be null");
        lock.lock();
        try {
            fillOpenSeats(); // A5: never let a newcomer take a seat an eligible waiter is entitled to

            if (roster.isEnrolled(id)) {
                return new EnrollResult.AlreadyEnrolled(id);
            }
            Optional<Integer> existing = roster.positionOf(id);
            if (existing.isPresent()) {
                return new EnrollResult.AlreadyWaitlisted(id, existing.get());
            }

            Optional<Set<CourseId>> completed = records.completedCourses(id);
            if (completed.isEmpty()) {
                return new EnrollResult.UnknownLearner(id);
            }
            Set<CourseId> missing = policy.missing(completed.get());
            if (!missing.isEmpty()) {
                return new EnrollResult.Ineligible(id, missing);
            }

            if (!roster.isFull()) {
                roster.enroll(id);
                return new EnrollResult.Enrolled(id);
            }
            return new EnrollResult.Waitlisted(id, roster.addToWaitlist(id));
        } finally {
            lock.unlock();
        }
    }

    public DropResult drop(LearnerId id) {
        Objects.requireNonNull(id, "learner id must not be null");
        lock.lock();
        try {
            if (!roster.isEnrolled(id)) {
                return records.completedCourses(id).isEmpty()
                        && !roster.isWaitlisted(id)
                        ? new DropResult.UnknownLearner(id)
                        : new DropResult.NotEnrolled(id);
            }
            roster.removeEnrolled(id);
            List<LearnerId> promoted = fillOpenSeats(); // same critical section: drop + promote is atomic
            return new DropResult.Dropped(id, promoted);
        } finally {
            lock.unlock();
        }
    }

    /** A9: a waitlisted learner withdraws. @return true if they were waiting and were removed. */
    public boolean leaveWaitlist(LearnerId id) {
        Objects.requireNonNull(id, "learner id must not be null");
        lock.lock();
        try {
            return roster.removeFromWaitlist(id);
        } finally {
            lock.unlock();
        }
    }

    /** Re-runs eligibility after learner records change. @return learners promoted into open seats. */
    public List<LearnerId> refreshWaitlist() {
        lock.lock();
        try {
            return fillOpenSeats();
        } finally {
            lock.unlock();
        }
    }

    /** Immutable copy of current state; never exposes internal collections. */
    public RosterSnapshot snapshot() {
        lock.lock();
        try {
            return roster.snapshot();
        } finally {
            lock.unlock();
        }
    }

    public Optional<Integer> positionOf(LearnerId id) {
        Objects.requireNonNull(id, "learner id must not be null");
        lock.lock();
        try {
            return roster.positionOf(id);
        } finally {
            lock.unlock();
        }
    }

    /** Precondition: caller holds {@link #lock}. */
    private List<LearnerId> fillOpenSeats() {
        return roster.fillOpenSeats(this::isEligibleNow);
    }

    /** Precondition: caller holds {@link #lock}. Unknown learners (record removed) are treated as ineligible. */
    private boolean isEligibleNow(LearnerId id) {
        return records.completedCourses(id).map(policy::isEligible).orElse(false);
    }
}

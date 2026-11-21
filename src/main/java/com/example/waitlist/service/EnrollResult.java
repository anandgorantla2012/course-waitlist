package com.example.waitlist.service;

import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.LearnerId;

import java.util.Set;

/** Expected business outcomes of an enroll request. Exceptions are reserved for programmer errors (null ids). */
public sealed interface EnrollResult {
    record Enrolled(LearnerId learner) implements EnrollResult {}

    /** @param position 1-based position in the waitlist */
    record Waitlisted(LearnerId learner, int position) implements EnrollResult {}

    record AlreadyEnrolled(LearnerId learner) implements EnrollResult {}

    record AlreadyWaitlisted(LearnerId learner, int position) implements EnrollResult {}

    record Ineligible(LearnerId learner, Set<CourseId> missing) implements EnrollResult {
        public Ineligible {
            missing = Set.copyOf(missing);
        }
    }

    record UnknownLearner(LearnerId learner) implements EnrollResult {}
}

package com.example.waitlist.port;

import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.LearnerId;

import java.util.Optional;
import java.util.Set;

/**
 * The one external dependency: what a learner has completed. Eligibility is re-evaluated at promotion
 * time, so implementations may return different answers over time.
 */
public interface LearnerRecords {
    /** Completed courses, or empty if the learner is unknown. */
    Optional<Set<CourseId>> completedCourses(LearnerId learner);
}

package com.example.waitlist.policy;

import com.example.waitlist.domain.CourseId;

import java.util.Set;

public interface EligibilityPolicy {
    /** @return the requirements the learner is missing; empty means eligible */
    Set<CourseId> missing(Set<CourseId> completed);

    default boolean isEligible(Set<CourseId> completed) {
        return missing(completed).isEmpty();
    }
}

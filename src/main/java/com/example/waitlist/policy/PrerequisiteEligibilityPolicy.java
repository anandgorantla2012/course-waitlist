package com.example.waitlist.policy;

import com.example.waitlist.domain.CourseId;

import java.util.Set;
import java.util.stream.Collectors;

/** A2: eligible iff every prerequisite is in the learner's completed set. No grades, no corequisites. */
public final class PrerequisiteEligibilityPolicy implements EligibilityPolicy {
    private final Set<CourseId> prerequisites;

    public PrerequisiteEligibilityPolicy(Set<CourseId> prerequisites) {
        this.prerequisites = Set.copyOf(prerequisites);
    }

    @Override
    public Set<CourseId> missing(Set<CourseId> completed) {
        return prerequisites.stream()
                .filter(p -> !completed.contains(p))
                .collect(Collectors.toUnmodifiableSet());
    }
}

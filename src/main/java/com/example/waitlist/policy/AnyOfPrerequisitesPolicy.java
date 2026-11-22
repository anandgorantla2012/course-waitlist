package com.example.waitlist.policy;

import java.util.Set;

import com.example.waitlist.domain.CourseId;

/** Eligible if the learner has completed AT LEAST ONE of the listed courses. */
public final class AnyOfPrerequisitesPolicy implements EligibilityPolicy {
    private final Set<CourseId> options;

    public AnyOfPrerequisitesPolicy(Set<CourseId> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("at least one option is required");
        }
        this.options = Set.copyOf(options);
    }

    /** Empty means eligible; otherwise reports the options, of which any one would have been enough. */
    @Override
    public Set<CourseId> missing(Set<CourseId> completed) {
        return options.stream().anyMatch(completed::contains) ? Set.of() : options;
    }
}
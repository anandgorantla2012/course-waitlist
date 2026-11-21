package com.example.waitlist.domain;

import java.util.Objects;
import java.util.Set;

/** The single course offering being managed: its id, fixed capacity and prerequisite set (A1, A2). */
public record Offering(CourseId courseId, int capacity, Set<CourseId> prerequisites) {
    public Offering {
        Objects.requireNonNull(courseId, "courseId must not be null");
        Objects.requireNonNull(prerequisites, "prerequisites must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        prerequisites = Set.copyOf(prerequisites); // immutable defensive copy (also rejects null elements)
    }
}

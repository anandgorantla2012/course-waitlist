package com.example.waitlist.domain;

import java.util.Objects;

/** Identifies a course. Validated: never null, never blank. */
public record CourseId(String value) {
    public CourseId {
        Objects.requireNonNull(value, "course id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("course id must not be blank");
        }
    }

    public static CourseId of(String value) {
        return new CourseId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

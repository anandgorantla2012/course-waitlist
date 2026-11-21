package com.example.waitlist.domain;

import java.util.Objects;

/** Identifies a learner. Validated: never null, never blank. */
public record LearnerId(String value) {
    public LearnerId {
        Objects.requireNonNull(value, "learner id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("learner id must not be blank");
        }
    }

    public static LearnerId of(String value) {
        return new LearnerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

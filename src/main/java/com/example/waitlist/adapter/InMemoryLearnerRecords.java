package com.example.waitlist.adapter;

import com.example.waitlist.domain.CourseId;
import com.example.waitlist.domain.LearnerId;
import com.example.waitlist.port.LearnerRecords;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Thread-safe in-memory {@link LearnerRecords} for the demo and tests. */
public final class InMemoryLearnerRecords implements LearnerRecords {
    private final ConcurrentHashMap<LearnerId, Set<CourseId>> completed = new ConcurrentHashMap<>();

    /** Registers (or replaces) a learner's completed courses. */
    public InMemoryLearnerRecords put(LearnerId learner, Set<CourseId> courses) {
        completed.put(learner, Set.copyOf(courses));
        return this;
    }

    public InMemoryLearnerRecords put(String learner, String... courses) {
        return put(LearnerId.of(learner),
                Arrays.stream(courses).map(CourseId::of).collect(Collectors.toUnmodifiableSet()));
    }

    /** Records one more completed course; registers the learner if unknown. */
    public void complete(LearnerId learner, CourseId course) {
        completed.merge(learner, Set.of(course), (a, b) ->
                java.util.stream.Stream.concat(a.stream(), b.stream()).collect(Collectors.toUnmodifiableSet()));
    }

    public void remove(LearnerId learner) {
        completed.remove(learner);
    }

    @Override
    public Optional<Set<CourseId>> completedCourses(LearnerId learner) {
        return Optional.ofNullable(completed.get(learner));
    }
}

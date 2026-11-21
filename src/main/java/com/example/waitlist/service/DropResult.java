package com.example.waitlist.service;

import com.example.waitlist.domain.LearnerId;

import java.util.List;

/** Expected business outcomes of a drop request. */
public sealed interface DropResult {
    /**
     * @param promoted learners promoted into open seats, in promotion order. Usually zero or one; it can
     *                 be more if earlier skipped waiters became eligible since the last refresh.
     */
    record Dropped(LearnerId learner, List<LearnerId> promoted) implements DropResult {
        public Dropped {
            promoted = List.copyOf(promoted);
        }
    }

    /** Not currently enrolled (never enrolled, only waitlisted, or already dropped). No state change. */
    record NotEnrolled(LearnerId learner) implements DropResult {}

    record UnknownLearner(LearnerId learner) implements DropResult {}
}

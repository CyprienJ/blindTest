package com.blindtest.model.game;

import java.util.Objects;
import java.util.Set;

/** Fixed V1 multiplayer skip-vote threshold from ADR-0019. */
public final class SkipVoteRule {

    public static final int THRESHOLD_PERCENT = 60;

    private SkipVoteRule() {
    }

    /** Returns the votes needed among connected participants, rounded up; at least one. */
    public static int requiredVotes(int connectedParticipants) {
        if (connectedParticipants < 1) {
            throw new IllegalArgumentException("connectedParticipants must be positive");
        }
        return (connectedParticipants * THRESHOLD_PERCENT + 99) / 100;
    }

    /** Returns whether the active participants' votes reach the threshold. */
    public static boolean passes(RoundScoreState state, Set<GameParticipantId> activeParticipants) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        if (activeParticipants.isEmpty()) {
            return false;
        }
        long votes = activeParticipants.stream().filter(state::skipVoted).count();
        return votes >= requiredVotes(activeParticipants.size());
    }
}

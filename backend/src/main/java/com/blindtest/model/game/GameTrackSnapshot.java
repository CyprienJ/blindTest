package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/** One selected track and its stable position in a game. */
public record GameTrackSnapshot(
        UUID trackId,
        int playOrder,
        GameAnswerSnapshot answers,
        GamePlaybackSnapshot playback,
        GameMediaSnapshot media) {

    /** Creates a complete immutable game-track snapshot. */
    public GameTrackSnapshot {
        Objects.requireNonNull(trackId, "trackId is required");
        if (playOrder < 0) {
            throw new IllegalArgumentException("playOrder cannot be negative");
        }
        Objects.requireNonNull(answers, "answers are required");
        Objects.requireNonNull(playback, "playback is required");
        Objects.requireNonNull(media, "media is required");
    }

    static GameTrackSnapshot atOrder(GameTrackCandidate candidate, int playOrder) {
        return new GameTrackSnapshot(
                candidate.trackId(),
                playOrder,
                candidate.answers(),
                candidate.playback(),
                candidate.media());
    }
}

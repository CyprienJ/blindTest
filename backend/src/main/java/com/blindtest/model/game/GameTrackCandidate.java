package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/** Detached playlist track projection supplied to the game selection boundary. */
public record GameTrackCandidate(
        UUID trackId,
        boolean eligiblePublished,
        GameAnswerSnapshot answers,
        GamePlaybackSnapshot playback,
        GameMediaSnapshot media) {

    /** Requires all data needed to snapshot a selected round. */
    public GameTrackCandidate {
        Objects.requireNonNull(trackId, "trackId is required");
        Objects.requireNonNull(answers, "answers are required");
        Objects.requireNonNull(playback, "playback is required");
        Objects.requireNonNull(media, "media is required");
    }
}

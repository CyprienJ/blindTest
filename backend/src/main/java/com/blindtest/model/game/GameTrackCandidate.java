package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/**
 * Detached playlist track projection supplied to the game selection boundary.
 *
 * @param difficulty administrator difficulty from 1 to 5, or {@code null} when unrated
 * @param streamingLinks listening links shown when the track is revealed
 */
public record GameTrackCandidate(
        UUID trackId,
        boolean eligiblePublished,
        GameAnswerSnapshot answers,
        GamePlaybackSnapshot playback,
        GameMediaSnapshot media,
        Integer difficulty,
        GameStreamingLinks streamingLinks) {

    /** Creates a candidate without streaming links. */
    public GameTrackCandidate(
            UUID trackId,
            boolean eligiblePublished,
            GameAnswerSnapshot answers,
            GamePlaybackSnapshot playback,
            GameMediaSnapshot media,
            Integer difficulty) {
        this(trackId, eligiblePublished, answers, playback, media, difficulty,
                GameStreamingLinks.NONE);
    }

    /** Creates an unrated candidate. */
    public GameTrackCandidate(
            UUID trackId,
            boolean eligiblePublished,
            GameAnswerSnapshot answers,
            GamePlaybackSnapshot playback,
            GameMediaSnapshot media) {
        this(trackId, eligiblePublished, answers, playback, media, null);
    }

    /** Requires all data needed to snapshot a selected round. */
    public GameTrackCandidate {
        Objects.requireNonNull(trackId, "trackId is required");
        Objects.requireNonNull(answers, "answers are required");
        Objects.requireNonNull(playback, "playback is required");
        Objects.requireNonNull(media, "media is required");
        Objects.requireNonNull(streamingLinks, "streamingLinks are required");
    }
}

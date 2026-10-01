package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/**
 * One selected track and its stable position in a game.
 *
 * @param difficulty administrator difficulty from 1 to 5 at selection, or {@code null}
 * @param streamingLinks listening links frozen at selection and shown on reveal
 * @param coverId stored cover frozen at selection and shown on reveal, or {@code null}
 */
public record GameTrackSnapshot(
        UUID trackId,
        int playOrder,
        GameAnswerSnapshot answers,
        GamePlaybackSnapshot playback,
        GameMediaSnapshot media,
        Integer difficulty,
        GameStreamingLinks streamingLinks,
        UUID coverId) {

    /** Creates a game-track snapshot without a cover. */
    public GameTrackSnapshot(
            UUID trackId,
            int playOrder,
            GameAnswerSnapshot answers,
            GamePlaybackSnapshot playback,
            GameMediaSnapshot media,
            Integer difficulty,
            GameStreamingLinks streamingLinks) {
        this(trackId, playOrder, answers, playback, media, difficulty, streamingLinks, null);
    }

    /** Creates a game-track snapshot without streaming links. */
    public GameTrackSnapshot(
            UUID trackId,
            int playOrder,
            GameAnswerSnapshot answers,
            GamePlaybackSnapshot playback,
            GameMediaSnapshot media,
            Integer difficulty) {
        this(trackId, playOrder, answers, playback, media, difficulty, GameStreamingLinks.NONE);
    }

    /** Creates an unrated game-track snapshot. */
    public GameTrackSnapshot(
            UUID trackId,
            int playOrder,
            GameAnswerSnapshot answers,
            GamePlaybackSnapshot playback,
            GameMediaSnapshot media) {
        this(trackId, playOrder, answers, playback, media, null);
    }

    /** Creates a complete immutable game-track snapshot. */
    public GameTrackSnapshot {
        Objects.requireNonNull(trackId, "trackId is required");
        if (playOrder < 0) {
            throw new IllegalArgumentException("playOrder cannot be negative");
        }
        Objects.requireNonNull(answers, "answers are required");
        Objects.requireNonNull(playback, "playback is required");
        Objects.requireNonNull(media, "media is required");
        Objects.requireNonNull(streamingLinks, "streamingLinks are required");
    }

    static GameTrackSnapshot atOrder(GameTrackCandidate candidate, int playOrder) {
        return new GameTrackSnapshot(
                candidate.trackId(),
                playOrder,
                candidate.answers(),
                candidate.playback(),
                candidate.media(),
                candidate.difficulty(),
                candidate.streamingLinks(),
                candidate.coverId());
    }
}

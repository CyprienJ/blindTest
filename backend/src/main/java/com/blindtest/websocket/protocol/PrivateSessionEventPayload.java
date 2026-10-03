package com.blindtest.websocket.protocol;

import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RoundOutcome;
import com.blindtest.model.game.RoundStatus;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Closed set of payloads that may be delivered only to one participant's user queue. */
public sealed interface PrivateSessionEventPayload permits
        PrivateSessionEventPayload.SecondaryMatch,
        PrivateSessionEventPayload.AnswerRejected,
        PrivateSessionEventPayload.CurrentSnapshot,
        PrivateSessionEventPayload.Error {

    /** Confirms a secondary answer without broadcasting another player's discovery. */
    record SecondaryMatch(GameAnswerType answerType, String matchedValue, boolean alreadyFound)
            implements PrivateSessionEventPayload {

        /** Restricts this event to non-title discoveries with safe display text. */
        public SecondaryMatch {
            Objects.requireNonNull(answerType, "answerType is required");
            if (answerType == GameAnswerType.TITLE) {
                throw new IllegalArgumentException("title matches are public round outcomes");
            }
            SessionProtocol.requireText(matchedValue, "matchedValue");
        }
    }

    /** Explains rejected answer feedback and optionally provides a cooldown deadline. */
    record AnswerRejected(AnswerRejectionReason reason, Optional<Instant> retryAt)
            implements PrivateSessionEventPayload {

        /** Requires retry timing only for cooldown rejection. */
        public AnswerRejected {
            Objects.requireNonNull(reason, "reason is required");
            Objects.requireNonNull(retryAt, "retryAt is required");
            if ((reason == AnswerRejectionReason.COOLDOWN) != retryAt.isPresent()) {
                throw new IllegalArgumentException("retryAt is required only for cooldown");
            }
        }
    }

    /** Complete reconnect projection, including only the recipient's private discoveries. */
    record CurrentSnapshot(
            GameStatus gameStatus,
            int currentRoundIndex,
            int totalRounds,
            int totalScore,
            Optional<CurrentRound> currentRound,
            List<GameAnswerType> discoveredAnswerTypes,
            Instant expiresAt,
            List<PublicSessionEventPayload.ParticipantScore> scores) implements PrivateSessionEventPayload {

        /** Defensively copies and validates the reconnect projection. */
        public CurrentSnapshot {
            Objects.requireNonNull(gameStatus, "gameStatus is required");
            if (currentRoundIndex < -1) {
                throw new IllegalArgumentException("currentRoundIndex cannot be below -1");
            }
            SessionProtocol.requirePositive(totalRounds, "totalRounds");
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
            Objects.requireNonNull(currentRound, "currentRound is required");
            Objects.requireNonNull(discoveredAnswerTypes, "discoveredAnswerTypes are required");
            discoveredAnswerTypes = List.copyOf(discoveredAnswerTypes);
            if (discoveredAnswerTypes.stream().anyMatch(Objects::isNull)
                    || discoveredAnswerTypes.contains(GameAnswerType.TITLE)) {
                throw new IllegalArgumentException("discoveries may contain only secondary types");
            }
            SessionProtocol.requireInstant(expiresAt, "expiresAt");
            scores = List.copyOf(scores);
        }
    }

    /** Safe current-round projection used to replace stale browser state after reconnect. */
    record CurrentRound(
            int roundIndex,
            RoundStatus status,
            Optional<Instant> startsAt,
            Optional<Instant> deadline,
            Optional<URI> playbackUrl,
            Optional<Integer> excerptStartSeconds,
            Optional<Integer> excerptDurationSeconds,
            Optional<RoundOutcome> outcome,
            Optional<String> title,
            Optional<String> artist,
            Optional<String> composer,
            Optional<String> album,
            Map<String, String> streamingLinks,
            Optional<String> coverUrl,
            Optional<Instant> advanceAt,
            Optional<Instant> pausedAt,
            int roundPoints,
            boolean titleFound,
            boolean skipVoted,
            int skipVotes,
            int requiredSkipVotes) {

        /** Defensively validates optional values without exposing object-storage keys. */
        public CurrentRound {
            SessionProtocol.requireNonNegative(roundIndex, "roundIndex");
            Objects.requireNonNull(status, "status is required");
            startsAt = Objects.requireNonNull(startsAt, "startsAt is required");
            deadline = Objects.requireNonNull(deadline, "deadline is required");
            playbackUrl = Objects.requireNonNull(playbackUrl, "playbackUrl is required");
            excerptStartSeconds = Objects.requireNonNull(
                    excerptStartSeconds, "excerptStartSeconds is required");
            excerptDurationSeconds = Objects.requireNonNull(
                    excerptDurationSeconds, "excerptDurationSeconds is required");
            outcome = Objects.requireNonNull(outcome, "outcome is required");
            title = requireOptionalText(title, "title");
            artist = requireOptionalText(artist, "artist");
            composer = requireOptionalText(composer, "composer");
            album = requireOptionalText(album, "album");
            streamingLinks = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(streamingLinks, "streamingLinks are required")));
            coverUrl = requireOptionalText(coverUrl, "coverUrl");
            advanceAt = Objects.requireNonNull(advanceAt, "advanceAt is required");
            pausedAt = Objects.requireNonNull(pausedAt, "pausedAt is required");
            excerptStartSeconds.ifPresent(
                    value -> SessionProtocol.requireNonNegative(value, "excerptStartSeconds"));
            excerptDurationSeconds.ifPresent(value -> {
                if (value <= 0) {
                    throw new IllegalArgumentException("excerptDurationSeconds must be positive");
                }
            });
            SessionProtocol.requireNonNegative(roundPoints, "roundPoints");
            SessionProtocol.requireNonNegative(skipVotes, "skipVotes");
            SessionProtocol.requirePositive(requiredSkipVotes, "requiredSkipVotes");
        }
    }

    /** Stable safe error payload for command-specific or asynchronous failures. */
    record Error(String code, String message, boolean recoverable)
            implements PrivateSessionEventPayload {

        /** Prevents blank public error fields. */
        public Error {
            SessionProtocol.requireText(code, "code");
            SessionProtocol.requireText(message, "message");
        }
    }

    private static Optional<String> requireOptionalText(
            Optional<String> value,
            String field) {
        Objects.requireNonNull(value, field + " is required");
        value.ifPresent(text -> SessionProtocol.requireText(text, field));
        return value;
    }
}

package com.blindtest.websocket.protocol;

import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RoundOutcome;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Closed set of payloads that may be broadcast to every authorized session member. */
public sealed interface PublicSessionEventPayload permits
        PublicSessionEventPayload.SessionReady,
        PublicSessionEventPayload.RoundPreparing,
        PublicSessionEventPayload.RoundStarted,
        PublicSessionEventPayload.RoundRevealed,
        PublicSessionEventPayload.ScoreUpdated,
        PublicSessionEventPayload.GameFinished,
        PublicSessionEventPayload.LobbyUpdated,
        PublicSessionEventPayload.LobbyStarted,
        PublicSessionEventPayload.SkipVoteUpdated {

    /** One participant's round and running scores, in join order. */
    record ParticipantScore(UUID participantId, String displayName, int roundPoints, int totalScore) {

        /** Validates identity and non-negative scores. */
        public ParticipantScore {
            Objects.requireNonNull(participantId, "participantId is required");
            SessionProtocol.requireText(displayName, "displayName");
            SessionProtocol.requireNonNegative(roundPoints, "roundPoints");
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
        }
    }

    /** One participant's final total, titles found, and shared rank, best first. */
    record ParticipantResult(
            UUID participantId,
            String displayName,
            int totalScore,
            int titlesFound,
            int rank) {

        /** Validates identity, totals, and rank. */
        public ParticipantResult {
            Objects.requireNonNull(participantId, "participantId is required");
            SessionProtocol.requireText(displayName, "displayName");
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
            SessionProtocol.requireNonNegative(titlesFound, "titlesFound");
            SessionProtocol.requirePositive(rank, "rank");
        }
    }

    /** Multiplayer skip-vote progress among connected participants (ADR-0019). */
    record SkipVoteUpdated(int votes, int requiredVotes) implements PublicSessionEventPayload {

        /** Validates vote counts. */
        public SkipVoteUpdated {
            SessionProtocol.requireNonNegative(votes, "votes");
            SessionProtocol.requirePositive(requiredVotes, "requiredVotes");
        }
    }

    /** One lobby member as broadcast to every member; never carries an account ID. */
    record LobbyMember(
            UUID participantId,
            String displayName,
            boolean ready,
            boolean host,
            boolean guest) {

        /** Validates the member projection. */
        public LobbyMember {
            Objects.requireNonNull(participantId, "participantId is required");
            SessionProtocol.requireText(displayName, "displayName");
        }
    }

    /** Current lobby membership, host, ready flags, and start eligibility. */
    record LobbyUpdated(
            String code,
            UUID hostParticipantId,
            List<LobbyMember> participants,
            boolean canStart) implements PublicSessionEventPayload {

        /** Copies the ordered member list. */
        public LobbyUpdated {
            SessionProtocol.requireText(code, "code");
            Objects.requireNonNull(hostParticipantId, "hostParticipantId is required");
            participants = List.copyOf(participants);
        }
    }

    /** The host started the lobby; the game session now exists under the same ID. */
    record LobbyStarted(int totalRounds) implements PublicSessionEventPayload {

        /** Validates the started game size. */
        public LobbyStarted {
            SessionProtocol.requirePositive(totalRounds, "totalRounds");
        }
    }

    /** Safe initial session projection emitted after an authorized subscription. */
    record SessionReady(GameStatus state, int totalRounds, Instant expiresAt)
            implements PublicSessionEventPayload {

        /** Validates the initial projection. */
        public SessionReady {
            Objects.requireNonNull(state, "state is required");
            SessionProtocol.requirePositive(totalRounds, "totalRounds");
            SessionProtocol.requireInstant(expiresAt, "expiresAt");
        }
    }

    /** Announces the next safe round index before playback details are available. */
    record RoundPreparing(int roundIndex, int totalRounds) implements PublicSessionEventPayload {

        /** Validates the ordered round position. */
        public RoundPreparing {
            validateRoundPosition(roundIndex, totalRounds);
        }
    }

    /**
     * Carries authoritative playback and scoring timestamps for a started round.
     *
     * @param excerptDurationSeconds window length clients loop from the excerpt start
     */
    record RoundStarted(
            int roundIndex,
            int totalRounds,
            Instant startsAt,
            Instant deadline,
            int excerptStartSeconds,
            int excerptDurationSeconds,
            Optional<URI> playbackUrl) implements PublicSessionEventPayload {

        /** Validates the playback window without treating the browser clock as authoritative. */
        public RoundStarted {
            validateRoundPosition(roundIndex, totalRounds);
            SessionProtocol.requireInstant(startsAt, "startsAt");
            SessionProtocol.requireInstant(deadline, "deadline");
            if (!deadline.isAfter(startsAt)) {
                throw new IllegalArgumentException("deadline must follow startsAt");
            }
            SessionProtocol.requireNonNegative(excerptStartSeconds, "excerptStartSeconds");
            if (excerptDurationSeconds <= 0) {
                throw new IllegalArgumentException("excerptDurationSeconds must be positive");
            }
            Objects.requireNonNull(playbackUrl, "playbackUrl is required");
        }
    }

    /**
     * Reveals canonical answers and the authoritative score for one terminal round.
     *
     * @param streamingLinks available HTTP(S) listening links keyed by platform
     * @param coverUrl same-origin path of the track cover, when one is stored
     */
    record RoundRevealed(
            int roundIndex,
            int totalRounds,
            RoundOutcome outcome,
            String title,
            String artist,
            Optional<String> composer,
            Optional<String> album,
            Map<String, String> streamingLinks,
            Optional<String> coverUrl,
            int roundPoints,
            int totalScore,
            Instant transitionAt,
            List<ParticipantScore> scores) implements PublicSessionEventPayload {

        /** Validates reveal metadata and non-negative authoritative scores. */
        public RoundRevealed {
            validateRoundPosition(roundIndex, totalRounds);
            Objects.requireNonNull(outcome, "outcome is required");
            SessionProtocol.requireText(title, "title");
            SessionProtocol.requireText(artist, "artist");
            composer = requireOptionalText(composer, "composer");
            album = requireOptionalText(album, "album");
            streamingLinks = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(streamingLinks, "streamingLinks are required")));
            coverUrl = requireOptionalText(coverUrl, "coverUrl");
            SessionProtocol.requireNonNegative(roundPoints, "roundPoints");
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
            SessionProtocol.requireInstant(transitionAt, "transitionAt");
            scores = List.copyOf(scores);
        }
    }

    /** Broadcasts one participant's authoritative score change. */
    record ScoreUpdated(UUID participantId, int roundPoints, int totalScore)
            implements PublicSessionEventPayload {

        /** Validates participant identity and score values. */
        public ScoreUpdated {
            Objects.requireNonNull(participantId, "participantId is required");
            SessionProtocol.requireNonNegative(roundPoints, "roundPoints");
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
        }
    }

    /** Publishes aggregate terminal totals without depending on history persistence. */
    record GameFinished(
            int totalScore,
            int totalRounds,
            int titlesFound,
            int skippedRounds,
            int timedOutRounds,
            List<ParticipantResult> standings) implements PublicSessionEventPayload {

        /** Validates aggregate outcome counts. */
        public GameFinished {
            SessionProtocol.requireNonNegative(totalScore, "totalScore");
            SessionProtocol.requirePositive(totalRounds, "totalRounds");
            SessionProtocol.requireNonNegative(titlesFound, "titlesFound");
            SessionProtocol.requireNonNegative(skippedRounds, "skippedRounds");
            SessionProtocol.requireNonNegative(timedOutRounds, "timedOutRounds");
            if (titlesFound + skippedRounds + timedOutRounds != totalRounds) {
                throw new IllegalArgumentException("game outcome totals are inconsistent");
            }
            standings = List.copyOf(standings);
        }
    }

    private static void validateRoundPosition(int roundIndex, int totalRounds) {
        SessionProtocol.requireNonNegative(roundIndex, "roundIndex");
        SessionProtocol.requirePositive(totalRounds, "totalRounds");
        if (roundIndex >= totalRounds) {
            throw new IllegalArgumentException("roundIndex must be below totalRounds");
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

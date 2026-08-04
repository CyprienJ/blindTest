package com.blindtest.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable aggregate snapshot of one completed game. */
@Entity
@Table(name = "game_histories")
@Getter
@Setter
@NoArgsConstructor
public class GameHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "history_version", nullable = false, length = 30)
    private String historyVersion;

    @Column(name = "game_mode", nullable = false, length = 20)
    private String gameMode;

    @Column(name = "protocol_version", nullable = false)
    private short protocolVersion;

    @Column(name = "configuration_version", nullable = false, length = 30)
    private String configurationVersion;

    @Column(name = "playlist_id_snapshot", nullable = false)
    private UUID playlistIdSnapshot;

    @Column(name = "requested_track_count", nullable = false)
    private int requestedTrackCount;

    @Column(name = "maximum_round_duration_seconds", nullable = false)
    private int maximumRoundDurationSeconds;

    @Column(name = "configuration_extensions", nullable = false, columnDefinition = "TEXT")
    private String configurationExtensions = "{}";

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "total_rounds", nullable = false)
    private int totalRounds;

    @Column(name = "titles_found", nullable = false)
    private int titlesFound;

    @Column(name = "skipped_rounds", nullable = false)
    private int skippedRounds;

    @Column(name = "timed_out_rounds", nullable = false)
    private int timedOutRounds;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "retention_expires_at", nullable = false)
    private Instant retentionExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @OneToMany(mappedBy = "gameHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private Set<GameHistoryParticipantEntity> participants = new LinkedHashSet<>();

    @OneToMany(mappedBy = "gameHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundIndex ASC")
    @Setter(AccessLevel.NONE)
    private List<GameHistoryRoundEntity> rounds = new ArrayList<>();

    /** Adds one participant snapshot while maintaining the bidirectional JPA association. */
    public void addParticipant(GameHistoryParticipantEntity participant) {
        participant.setGameHistory(this);
        participants.add(participant);
    }

    /** Adds one ordered round snapshot while maintaining the bidirectional JPA association. */
    public void addRound(GameHistoryRoundEntity round) {
        round.setGameHistory(this);
        rounds.add(round);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

package com.blindtest.model.entity;

import com.blindtest.model.game.RoundOutcome;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ordered immutable answer, outcome, timing, and score snapshot for one completed round. */
@Entity
@Table(name = "game_history_rounds")
@Getter
@Setter
@NoArgsConstructor
public class GameHistoryRoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_history_id", nullable = false)
    private GameHistoryEntity gameHistory;

    @Column(name = "round_index", nullable = false)
    private int roundIndex;

    @Column(name = "track_id_snapshot")
    private UUID trackIdSnapshot;

    @Column(name = "title_snapshot", nullable = false, length = 255)
    private String titleSnapshot;

    @Column(name = "artist_snapshot", nullable = false, length = 255)
    private String artistSnapshot;

    @Column(name = "composer_snapshot", length = 255)
    private String composerSnapshot;

    @Column(name = "album_snapshot", length = 255)
    private String albumSnapshot;

    @Column(name = "answer_aliases_snapshot", nullable = false, columnDefinition = "TEXT")
    private String answerAliasesSnapshot = "{}";

    @Column(name = "answer_snapshot_version", nullable = false, length = 30)
    private String answerSnapshotVersion;

    @Column(name = "excerpt_start_seconds", nullable = false)
    private int excerptStartSeconds;

    @Column(name = "excerpt_duration_seconds", nullable = false)
    private int excerptDurationSeconds;

    /** Track difficulty when the game was created; {@code null} for unrated tracks. */
    @Column(name = "difficulty_snapshot")
    private Short difficultySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RoundOutcome outcome;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "scoring_rule_version", nullable = false, length = 30)
    private String scoringRuleVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** Per-participant results; empty for solo rounds, whose points are the owner's. */
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private List<GameHistoryRoundResultEntity> results = new ArrayList<>();

    /** Adds one participant result while maintaining the bidirectional JPA association. */
    public void addResult(GameHistoryRoundResultEntity result) {
        result.setRound(this);
        results.add(result);
    }
}

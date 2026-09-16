package com.blindtest.model.entity;

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
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable identity and final-score snapshot for one game participant. */
@Entity
@Table(name = "game_history_participants")
@Getter
@Setter
@NoArgsConstructor
public class GameHistoryParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_history_id", nullable = false)
    private GameHistoryEntity gameHistory;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_kind", nullable = false, length = 20)
    private HistoryParticipantKind participantKind;

    @Column(name = "owner", nullable = false)
    private boolean owner;

    @Column(name = "final_score", nullable = false)
    private int finalScore;

    @Column(name = "final_rank", nullable = false)
    private int finalRank;
}

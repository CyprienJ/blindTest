package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One multiplayer participant's title result and points for one completed round. */
@Entity
@Table(name = "game_history_round_results")
@Getter
@Setter
@NoArgsConstructor
public class GameHistoryRoundResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_history_round_id", nullable = false)
    private GameHistoryRoundEntity round;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "title_found", nullable = false)
    private boolean titleFound;

    @Column(name = "points", nullable = false)
    private int points;
}

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
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Manually configured alternative answer for one track metadata field. */
@Entity
@Table(
        name = "track_answer_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_track_answer_aliases_normalized",
                columnNames = {"track_id", "answer_type", "normalized_value"}))
@Getter
@Setter
@NoArgsConstructor
public class TrackAnswerAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private TrackEntity track;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 20)
    private TrackAnswerType answerType;

    @Column(name = "alias_value", nullable = false, length = 255)
    private String value;

    @Column(name = "normalized_value", nullable = false, length = 512)
    private String normalizedValue;

    /** Creates an alias with its internal duplicate-detection key. */
    public TrackAnswerAliasEntity(
            TrackEntity track,
            TrackAnswerType answerType,
            String value,
            String normalizedValue) {
        this.track = track;
        this.answerType = answerType;
        this.value = value;
        this.normalizedValue = normalizedValue;
    }
}

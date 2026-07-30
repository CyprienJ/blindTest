package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity mapped to the {@code tracks} table. */
@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
public class TrackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    /** Canonical primary answer: film title for film tracks, music title otherwise. */
    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String artist;

    @Column(length = 255)
    private String album;

    @Column(length = 255)
    private String composer;

    @Column(length = 255)
    private String work;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "audio_object_key", length = 1024)
    private String audioObjectKey;

    @Column(name = "audio_checksum", length = 64)
    private String audioChecksum;

    @Column(name = "audio_size_bytes")
    private Long audioSizeBytes;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 100)
    private String category;

    private Short decade;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "excerpt_start_seconds", nullable = false)
    private int excerptStartSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrackStatus status = TrackStatus.PENDING;

    @Column(name = "youtube_url", length = 500)
    private String youtubeUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

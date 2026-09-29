package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackImportJobEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response DTO representing the current state of a track import job. */
@Getter
@AllArgsConstructor
public class ImportJobResponse {

    private UUID jobId;
    private String source;
    private String originalFilename;
    private String status;
    private int progress;
    private String errorMessage;
    private boolean previewAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private TrackAdminResponse track;

    /** Creates a polling snapshot without exposing internal media identifiers. */
    public static ImportJobResponse from(TrackImportJobEntity entity, boolean previewAvailable) {
        TrackAdminResponse track = entity.getTrack() != null ?
                TrackAdminResponse.from(entity.getTrack()) : null;
        return new ImportJobResponse(
                entity.getId(), entity.getSource().name(), entity.getOriginalFilename(),
                entity.getStatus().name(),
                entity.getProgress(), entity.getErrorMessage(),
                previewAvailable, entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getCompletedAt(), track);
    }
}

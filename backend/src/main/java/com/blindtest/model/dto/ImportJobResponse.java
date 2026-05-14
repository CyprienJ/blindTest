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
    private String status;
    private int progress;
    private String errorMessage;
    private LocalDateTime createdAt;
    private TrackAdminResponse track;

    /** Creates a response DTO from the given entity; {@code track} is null unless the job is done. */
    public static ImportJobResponse from(TrackImportJobEntity entity) {
        TrackAdminResponse track = entity.getTrack() != null ?
                TrackAdminResponse.from(entity.getTrack()) : null;
        return new ImportJobResponse(
                entity.getId(), entity.getStatus().name(),
                entity.getProgress(), entity.getErrorMessage(),
                entity.getCreatedAt(), track);
    }
}

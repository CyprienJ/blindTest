package com.blindtest.model.entity;

/** Processing lifecycle status of a {@link TrackImportJobEntity}. */
public enum ImportJobStatus {
    QUEUED, DOWNLOADING, PROCESSING, DONE, PUBLISHED, REJECTED, FAILED
}

package com.blindtest.model.dto;

import java.time.Instant;
import java.util.List;

/** Point-in-time comparison of database media references with stored objects. */
public record MediaReconciliationReport(
        Instant checkedAt,
        int missingCount,
        int orphanedCount,
        List<MissingMediaObject> missingObjects,
        List<String> orphanedObjectFingerprints) {
}

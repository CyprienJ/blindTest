package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response body for POST /api/admin/tracks/import. */
@Getter
@AllArgsConstructor
public class ImportStartResponse {

    private UUID jobId;
}

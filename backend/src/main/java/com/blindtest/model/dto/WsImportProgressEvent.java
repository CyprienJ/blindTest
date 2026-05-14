package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** WebSocket event pushed on each yt-dlp progress tick for an import job. */
@Getter
@AllArgsConstructor
public class WsImportProgressEvent {

    private UUID jobId;
    private String status;
    private int progress;
}

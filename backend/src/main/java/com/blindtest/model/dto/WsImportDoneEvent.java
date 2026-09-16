package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** WebSocket event pushed when an import job completes successfully. */
@Getter
@AllArgsConstructor
public class WsImportDoneEvent {

    private UUID jobId;
    private UUID trackId;
}

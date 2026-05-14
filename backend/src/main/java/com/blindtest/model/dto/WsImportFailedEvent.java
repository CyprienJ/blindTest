package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** WebSocket event pushed when an import job fails. */
@Getter
@AllArgsConstructor
public class WsImportFailedEvent {

    private UUID jobId;
    private String errorMessage;
}

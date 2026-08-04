package com.blindtest.model.dto;

/** STOMP endpoints and destinations needed to connect to an active session. */
public record SessionConnectionInstructions(
        String webSocketEndpoint,
        String sockJsEndpoint,
        String commandDestination,
        String eventDestination,
        String privateDestination) {
}

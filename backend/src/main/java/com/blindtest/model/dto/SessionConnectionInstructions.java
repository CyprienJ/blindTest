package com.blindtest.model.dto;

import com.blindtest.websocket.protocol.SessionProtocol;
import java.util.UUID;

/** STOMP endpoints and destinations needed to connect to an active session. */
public record SessionConnectionInstructions(
        int protocolVersion,
        String webSocketEndpoint,
        String sockJsEndpoint,
        String commandDestination,
        String eventDestination,
        String privateDestination) {

    /** Returns the protocol V1 endpoints and destinations of one session. */
    public static SessionConnectionInstructions forSession(UUID sessionId) {
        return new SessionConnectionInstructions(
                SessionProtocol.VERSION,
                SessionProtocol.WEB_SOCKET_ENDPOINT,
                SessionProtocol.SOCK_JS_ENDPOINT,
                SessionProtocol.commandDestination(sessionId),
                SessionProtocol.publicEventDestination(sessionId),
                SessionProtocol.privateEventDestination(sessionId));
    }
}

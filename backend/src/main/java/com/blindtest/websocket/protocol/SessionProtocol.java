package com.blindtest.websocket.protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Constants, destinations, and shared invariants for the gameplay STOMP protocol. */
public final class SessionProtocol {

    public static final int VERSION = 1;
    public static final String WEB_SOCKET_ENDPOINT = "/ws";
    public static final String SOCK_JS_ENDPOINT = "/ws-sockjs";
    public static final String COMMAND_DESTINATION_PREFIX = "/app/sessions/";
    public static final String PUBLIC_EVENT_DESTINATION_PREFIX = "/topic/sessions/";
    public static final String PRIVATE_EVENT_DESTINATION_PREFIX = "/user/queue/sessions/";

    private SessionProtocol() {
    }

    /** Returns the single inbound command destination for a session. */
    public static String commandDestination(UUID sessionId) {
        return COMMAND_DESTINATION_PREFIX
                + Objects.requireNonNull(sessionId, "sessionId is required");
    }

    /** Returns the shared outbound event destination for session members. */
    public static String publicEventDestination(UUID sessionId) {
        return PUBLIC_EVENT_DESTINATION_PREFIX
                + Objects.requireNonNull(sessionId, "sessionId is required");
    }

    /** Returns the per-user outbound queue destination for private feedback. */
    public static String privateEventDestination(UUID sessionId) {
        return PRIVATE_EVENT_DESTINATION_PREFIX
                + Objects.requireNonNull(sessionId, "sessionId is required");
    }

    /** Returns the server-side user queue suffix used with convertAndSendToUser. */
    public static String privateEventQueue(UUID sessionId) {
        return "/queue/sessions/" + Objects.requireNonNull(sessionId, "sessionId is required");
    }

    static void requireProtocolVersion(int protocolVersion) {
        if (protocolVersion != VERSION) {
            throw new IllegalArgumentException("unsupported session protocol version");
        }
    }

    static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static void requireOptionalText(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    static Instant requireInstant(Instant value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }
}

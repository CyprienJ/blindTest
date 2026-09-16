package com.blindtest.websocket;

import com.blindtest.websocket.protocol.PrivateSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Routes gameplay events to their shared topic or authenticated user's private queue. */
@Component
@RequiredArgsConstructor
public class SessionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /** Publishes an event to every authorized subscriber of its session. */
    public void publishPublic(PublicSessionEvent event) {
        Objects.requireNonNull(event, "event is required");
        messagingTemplate.convertAndSend(
                SessionProtocol.publicEventDestination(event.metadata().sessionId()), event);
    }

    /** Publishes feedback only to the authenticated account's session-specific user queue. */
    public void publishPrivate(UUID accountId, PrivateSessionEvent event) {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(event, "event is required");
        messagingTemplate.convertAndSendToUser(
                accountId.toString(),
                SessionProtocol.privateEventQueue(event.metadata().sessionId()),
                event);
    }
}

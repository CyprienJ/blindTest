package com.blindtest.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.blindtest.model.game.GameStatus;
import com.blindtest.websocket.protocol.PrivateSessionEvent;
import com.blindtest.websocket.protocol.PrivateSessionEventPayload;
import com.blindtest.websocket.protocol.PrivateSessionEventType;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEventPayload;
import com.blindtest.websocket.protocol.PublicSessionEventType;
import com.blindtest.websocket.protocol.SessionEventMetadata;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class SessionEventPublisherTest {

    private static final UUID SESSION_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PARTICIPANT_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant NOW = Instant.parse("2026-08-04T14:00:00Z");

    @Test
    void routesPublicAndPrivateEventsThroughSeparateDestinations() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        SessionEventPublisher publisher = new SessionEventPublisher(template);
        PublicSessionEvent publicEvent = new PublicSessionEvent(
                metadata(),
                PublicSessionEventType.SESSION_READY,
                new PublicSessionEventPayload.SessionReady(
                        GameStatus.CREATED, 5, NOW.plusSeconds(1800)));
        PrivateSessionEvent privateEvent = new PrivateSessionEvent(
                metadata(),
                PARTICIPANT_ID,
                Optional.empty(),
                PrivateSessionEventType.ERROR,
                new PrivateSessionEventPayload.Error(
                        "SESSION_EXPIRED", "The session has expired.", false));

        publisher.publishPublic(publicEvent);
        publisher.publishPrivate(ACCOUNT_ID, privateEvent);

        verify(template).convertAndSend(
                SessionProtocol.publicEventDestination(SESSION_ID), publicEvent);
        verify(template).convertAndSendToUser(
                ACCOUNT_ID.toString(),
                SessionProtocol.privateEventQueue(SESSION_ID),
                privateEvent);
    }

    private SessionEventMetadata metadata() {
        return new SessionEventMetadata(
                SessionProtocol.VERSION, UUID.randomUUID(), SESSION_ID, 0, NOW);
    }
}

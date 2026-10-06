package com.blindtest.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.lobby.AudioMode;
import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbyParticipant;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.security.GuestToken;
import com.blindtest.store.LobbyStore;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class SessionDestinationAuthorizationInterceptorTest {

    private static final UUID SESSION_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MEMBER_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-04T14:00:00Z");

    private SessionStore sessionStore;
    private LobbyStore lobbyStore;
    private SessionDestinationAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        sessionStore = mock(SessionStore.class);
        lobbyStore = mock(LobbyStore.class);
        interceptor = new SessionDestinationAuthorizationInterceptor(
                sessionStore, lobbyStore, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authorizesLobbyMembersBeforeTheGameSessionExists() {
        UUID intruderId = UUID.randomUUID();
        when(sessionStore.find(new SessionId(SESSION_ID), NOW)).thenReturn(Optional.empty());
        LobbyParticipant host = new LobbyParticipant(
                new GameParticipantId(UUID.randomUUID()), Optional.of(MEMBER_ID), "Host", false);
        LobbySnapshot lobby = LobbySnapshot.open(
                new SessionId(SESSION_ID),
                new LobbyCode("ABCDEF"),
                host,
                new RequestedGameConfiguration(UUID.randomUUID(), 5, 30, java.util.Map.of()),
                AudioMode.EVERYONE,
                NOW,
                NOW.plusSeconds(1800));
        when(lobbyStore.find(new SessionId(SESSION_ID), NOW)).thenReturn(Optional.of(lobby));

        Message<byte[]> subscription = destinationMessage(
                StompCommand.SUBSCRIBE, SessionProtocol.publicEventDestination(SESSION_ID), MEMBER_ID);
        assertThat(interceptor.preSend(subscription, null)).isSameAs(subscription);
        assertDenied(destinationMessage(
                StompCommand.SUBSCRIBE,
                SessionProtocol.publicEventDestination(SESSION_ID),
                intruderId), "Session is unavailable or access is denied");
    }

    @Test
    void authorizesMemberCommandsAndBothSubscriptionKinds() {
        stubActiveMember(MEMBER_ID);

        for (Message<byte[]> message : List.of(
                destinationMessage(
                        StompCommand.SEND,
                        SessionProtocol.commandDestination(SESSION_ID),
                        MEMBER_ID),
                destinationMessage(
                        StompCommand.SUBSCRIBE,
                        SessionProtocol.publicEventDestination(SESSION_ID),
                        MEMBER_ID),
                destinationMessage(
                        StompCommand.SUBSCRIBE,
                        SessionProtocol.privateEventDestination(SESSION_ID),
                        MEMBER_ID))) {
            assertThat(interceptor.preSend(message, null)).isSameAs(message);
        }
    }

    @Test
    void rejectsNonMemberAndUnavailableSessionWithoutRevealingWhichFailed() {
        UUID intruderId = UUID.randomUUID();
        stubActiveMember(MEMBER_ID);

        assertDenied(destinationMessage(
                StompCommand.SUBSCRIBE,
                SessionProtocol.publicEventDestination(SESSION_ID),
                intruderId), "Session is unavailable or access is denied");

        when(sessionStore.find(new SessionId(SESSION_ID), NOW)).thenReturn(Optional.empty());
        assertDenied(destinationMessage(
                StompCommand.SEND,
                SessionProtocol.commandDestination(SESSION_ID),
                MEMBER_ID), "Session is unavailable or access is denied");
    }

    @Test
    void rejectsMalformedCrossChannelAndDirectBrokerDestinations() {
        stubActiveMember(MEMBER_ID);

        for (String destination : List.of(
                "/app/sessions/not-a-uuid",
                "/app/sessions/1-1-1-1-1",
                SessionProtocol.commandDestination(SESSION_ID) + "/answer",
                SessionProtocol.publicEventDestination(SESSION_ID),
                SessionProtocol.privateEventDestination(SESSION_ID),
                "/queue/sessions/" + SESSION_ID,
                "/user/" + MEMBER_ID + "/queue/sessions/" + SESSION_ID)) {
            assertDenied(destinationMessage(
                    StompCommand.SEND, destination, MEMBER_ID), "Invalid gameplay destination");
        }

        assertDenied(destinationMessage(
                StompCommand.SUBSCRIBE,
                SessionProtocol.commandDestination(SESSION_ID),
                MEMBER_ID), "Invalid gameplay destination");
    }

    @Test
    void rejectsUnauthenticatedGameplayFrameButLeavesUnrelatedDestinationsAlone() {
        Message<byte[]> unauthenticated = destinationMessage(
                StompCommand.SUBSCRIBE,
                SessionProtocol.publicEventDestination(SESSION_ID),
                null);
        assertDenied(unauthenticated, "Authentication is required");

        Message<byte[]> importSubscription = destinationMessage(
                StompCommand.SUBSCRIBE, "/user/queue/import/123", MEMBER_ID);
        assertThat(interceptor.preSend(importSubscription, null)).isSameAs(importSubscription);
    }

    @Test
    void authorizesGuestsOnlyInTheSessionTheirTokenNames() {
        UUID guestParticipantId = UUID.randomUUID();
        ActiveSessionSnapshot snapshot = mock(ActiveSessionSnapshot.class);
        when(snapshot.participants()).thenReturn(List.of(new SessionParticipant(
                new GameParticipantId(guestParticipantId), Optional.empty(), "Guest")));
        when(sessionStore.find(new SessionId(SESSION_ID), NOW)).thenReturn(Optional.of(snapshot));

        Message<byte[]> own = guestMessage(
                SessionProtocol.privateEventDestination(SESSION_ID),
                new GuestToken(SESSION_ID, guestParticipantId));
        assertThat(interceptor.preSend(own, null)).isSameAs(own);
        assertDenied(guestMessage(
                SessionProtocol.privateEventDestination(SESSION_ID),
                new GuestToken(UUID.randomUUID(), guestParticipantId)),
                "Session is unavailable or access is denied");
    }

    private Message<byte[]> guestMessage(String destination, GuestToken guest) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(guest.participantId(), null);
        authentication.setDetails(guest);
        accessor.setUser(authentication);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void stubActiveMember(UUID accountId) {
        ActiveSessionSnapshot snapshot = mock(ActiveSessionSnapshot.class);
        SessionParticipant participant = new SessionParticipant(
                new GameParticipantId(UUID.randomUUID()), Optional.of(accountId), "Member");
        when(snapshot.participants()).thenReturn(List.of(participant));
        when(sessionStore.find(new SessionId(SESSION_ID), NOW)).thenReturn(Optional.of(snapshot));
    }

    private Message<byte[]> destinationMessage(
            StompCommand command,
            String destination,
            UUID accountId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (accountId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(accountId, null));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void assertDenied(Message<byte[]> message, String expectedMessage) {
        assertThatThrownBy(() -> interceptor.preSend(message, null)).
                isInstanceOf(MessageDeliveryException.class).
                hasMessageContaining(expectedMessage);
    }
}

package com.blindtest.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.entity.UserRole;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameConfigurationValidator;
import com.blindtest.model.game.GameMediaSnapshot;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GamePlaybackSnapshot;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.GameTrackSnapshot;
import com.blindtest.model.game.PlaylistEligibilitySnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.protocol.PrivateSessionEvent;
import com.blindtest.websocket.protocol.PrivateSessionEventPayload;
import com.blindtest.websocket.protocol.PrivateSessionEventType;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEventPayload;
import com.blindtest.websocket.protocol.PublicSessionEventType;
import com.blindtest.websocket.protocol.SessionCommand;
import com.blindtest.websocket.protocol.SessionCommandType;
import com.blindtest.websocket.protocol.SessionEventMetadata;
import com.blindtest.websocket.protocol.SessionProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({
    SessionWebSocketAuthorizationIntegrationTest.ClockConfiguration.class,
    SessionWebSocketAuthorizationIntegrationTest.ProbeConfiguration.class
})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sessionwebsocket;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.access-token.ttl-ms=60000"
})
class SessionWebSocketAuthorizationIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-04T14:00:00Z");
    private static final Duration WAIT = Duration.ofSeconds(5);

    private static WebSocketStompClient stompClient;

    @LocalServerPort
    private int port;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SessionEventPublisher eventPublisher;

    @Autowired
    private SubscriptionProbe subscriptionProbe;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    @BeforeEach
    void setUp() {
        clock.set(INITIAL_TIME);
        subscriptionProbe.clear();
        if (stompClient == null) {
            stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        }
    }

    @AfterAll
    static void stopClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void memberReceivesSharedAndPrivateEventsOverRealStompConnection() throws Exception {
        UUID accountId = UUID.randomUUID();
        ActiveSessionSnapshot activeSession = createSession(accountId);
        RecordingSessionHandler connectionHandler = new RecordingSessionHandler();
        StompSession connection = connect(accessToken(accountId), connectionHandler);
        BlockingQueue<byte[]> publicFrames = new LinkedBlockingQueue<>();
        BlockingQueue<byte[]> privateFrames = new LinkedBlockingQueue<>();
        subscribeAndAwait(connection,
                SessionProtocol.publicEventDestination(activeSession.sessionId().value()),
                bytesHandler(publicFrames));
        subscribeAndAwait(connection,
                SessionProtocol.privateEventDestination(activeSession.sessionId().value()),
                bytesHandler(privateFrames));

        eventPublisher.publishPublic(sessionReady(activeSession));
        eventPublisher.publishPrivate(accountId, privateError(activeSession));

        JsonNode publicJson = readFrame(publicFrames);
        JsonNode privateJson = readFrame(privateFrames);
        assertThat(publicJson.get("type").asText()).isEqualTo("SESSION_READY");
        assertThat(privateJson.get("type").asText()).isEqualTo("ERROR");
        assertThat(privateJson.get("recipientParticipantId").asText()).
                isEqualTo(activeSession.ownerParticipantId().value().toString());
        assertThat(connectionHandler.failure()).isNull();
        connection.disconnect();
    }

    @Test
    void nonMemberCannotObserveOrCommandAnotherSession() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        ActiveSessionSnapshot activeSession = createSession(ownerId);

        RecordingSessionHandler subscriptionHandler = new RecordingSessionHandler();
        StompSession subscriptionConnection = connect(
                accessToken(intruderId), subscriptionHandler);
        subscriptionConnection.subscribe(
                SessionProtocol.publicEventDestination(activeSession.sessionId().value()),
                bytesHandler(new LinkedBlockingQueue<>()));
        assertThat(subscriptionHandler.awaitFailure()).isNotNull();

        RecordingSessionHandler commandHandler = new RecordingSessionHandler();
        StompSession commandConnection = connect(accessToken(intruderId), commandHandler);
        sendCommand(commandConnection, activeSession, SessionCommandType.START_GAME);
        assertThat(commandHandler.awaitFailure()).isNotNull();
        assertThat(sessionStore.find(activeSession.sessionId(), clock.instant()).orElseThrow().
                version()).isZero();
    }

    @Test
    void memberCanSendAuthorizedCommandOverRealStompConnection() throws Exception {
        UUID accountId = UUID.randomUUID();
        ActiveSessionSnapshot activeSession = createSession(accountId);
        RecordingSessionHandler handler = new RecordingSessionHandler();
        StompSession connection = connect(accessToken(accountId), handler);
        BlockingQueue<byte[]> privateFrames = new LinkedBlockingQueue<>();
        subscribeAndAwait(
                connection,
                SessionProtocol.privateEventDestination(activeSession.sessionId().value()),
                bytesHandler(privateFrames));

        sendCommand(connection, activeSession, SessionCommandType.REQUEST_SNAPSHOT);

        JsonNode received = readFrame(privateFrames);
        assertThat(received.get("type").asText()).isEqualTo("CURRENT_SNAPSHOT");
        assertThat(received.at("/metadata/sessionId").asText()).
                isEqualTo(activeSession.sessionId().value().toString());
        assertThat(handler.failure()).isNull();
        connection.disconnect();
    }

    @Test
    void existingConnectionSurvivesTokenExpiryButReconnectRequiresFreshToken() throws Exception {
        UUID accountId = UUID.randomUUID();
        ActiveSessionSnapshot activeSession = createSession(accountId);
        String token = accessToken(accountId);
        RecordingSessionHandler connectedHandler = new RecordingSessionHandler();
        StompSession connected = connect(token, connectedHandler);
        BlockingQueue<byte[]> privateFrames = new LinkedBlockingQueue<>();
        subscribeAndAwait(
                connected,
                SessionProtocol.privateEventDestination(activeSession.sessionId().value()),
                bytesHandler(privateFrames));

        clock.advance(Duration.ofSeconds(61));
        sendCommand(connected, activeSession, SessionCommandType.REQUEST_SNAPSHOT);

        assertThat(readFrame(privateFrames).get("type").asText()).
                isEqualTo("CURRENT_SNAPSHOT");
        assertThat(connectedHandler.failure()).isNull();
        RecordingSessionHandler reconnectHandler = new RecordingSessionHandler();
        assertThatThrownBy(() -> connect(token, reconnectHandler)).
                isInstanceOf(Exception.class);
        connected.disconnect();
    }

    @Test
    void realStompConnectRejectsMissingAuthentication() {
        RecordingSessionHandler handler = new RecordingSessionHandler();

        assertThatThrownBy(() -> connect(null, handler)).isInstanceOf(Exception.class);
    }

    @Test
    void realStompHandshakeRejectsUntrustedOrigin() {
        RecordingSessionHandler handler = new RecordingSessionHandler();

        assertThatThrownBy(() -> connect(
                accessToken(UUID.randomUUID()), handler, "https://untrusted.example")).
                isInstanceOf(Exception.class);
    }

    private StompSession connect(String token, RecordingSessionHandler handler) throws Exception {
        return connect(token, handler, null);
    }

    private StompSession connect(String token, RecordingSessionHandler handler, String origin)
            throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        if (origin != null) {
            handshakeHeaders.setOrigin(origin);
        }
        return stompClient.connectAsync(
                "ws://127.0.0.1:" + port + SessionProtocol.WEB_SOCKET_ENDPOINT,
                handshakeHeaders,
                connectHeaders,
                handler).
                get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private void sendCommand(
            StompSession connection,
            ActiveSessionSnapshot activeSession,
            SessionCommandType type) throws Exception {
        SessionCommand command = new SessionCommand(
                SessionProtocol.VERSION,
                UUID.randomUUID(),
                activeSession.version(),
                type,
                null);
        StompHeaders headers = new StompHeaders();
        headers.setDestination(SessionProtocol.commandDestination(
                activeSession.sessionId().value()));
        headers.setContentType(MediaType.APPLICATION_JSON);
        connection.send(headers, objectMapper.writeValueAsBytes(command));
    }

    private void subscribeAndAwait(
            StompSession connection,
            String destination,
            StompFrameHandler handler) throws InterruptedException {
        connection.subscribe(destination, handler);
        ProbedSubscription subscription = subscriptionProbe.poll(WAIT);
        assertThat(subscription).isNotNull();
        assertThat(subscription.destination()).isEqualTo(destination);
    }

    private ActiveSessionSnapshot createSession(UUID accountId) {
        UUID sessionId = UUID.randomUUID();
        GameParticipantId participantId = new GameParticipantId(UUID.randomUUID());
        UUID playlistId = UUID.randomUUID();
        GameConfiguration configuration = new GameConfigurationValidator().validate(
                new RequestedGameConfiguration(playlistId, 5, 30, Map.of()),
                new PlaylistEligibilitySnapshot(playlistId, 5));
        GameStateSnapshot state = GameStateSnapshot.created(
                configuration, new GameTrackSelection(tracks()), List.of(participantId));
        ActiveSessionSnapshot session = new ActiveSessionSnapshot(
                new SessionId(sessionId),
                participantId,
                List.of(new SessionParticipant(
                        participantId, Optional.of(accountId), "Member")),
                configuration,
                state,
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                0,
                clock.instant(),
                clock.instant(),
                clock.instant().plus(Duration.ofMinutes(30)), 0);
        assertThat(sessionStore.create(session, clock.instant())).
                isEqualTo(SessionCreateOutcome.CREATED);
        return session;
    }

    private List<GameTrackSnapshot> tracks() {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            tracks.add(new GameTrackSnapshot(
                    UUID.randomUUID(),
                    index,
                    new GameAnswerSnapshot(
                            "Title " + index, "Artist " + index, null, null, Map.of()),
                    new GamePlaybackSnapshot(0, 180),
                    new GameMediaSnapshot(
                            "tracks/" + index + "/audio.mp3", "checksum-" + index)));
        }
        return tracks;
    }

    private String accessToken(UUID accountId) {
        return jwtTokenProvider.generateAccessToken(accountId, UserRole.USER);
    }

    private PublicSessionEvent sessionReady(ActiveSessionSnapshot session) {
        return new PublicSessionEvent(
                metadata(session),
                PublicSessionEventType.SESSION_READY,
                new PublicSessionEventPayload.SessionReady(
                        GameStatus.CREATED,
                        session.configuration().requestedTrackCount(),
                        session.expiresAt()));
    }

    private PrivateSessionEvent privateError(ActiveSessionSnapshot session) {
        return new PrivateSessionEvent(
                metadata(session),
                session.ownerParticipantId().value(),
                Optional.empty(),
                PrivateSessionEventType.ERROR,
                new PrivateSessionEventPayload.Error(
                        "TEST_PRIVATE", "Private test event.", true));
    }

    private SessionEventMetadata metadata(ActiveSessionSnapshot session) {
        return new SessionEventMetadata(
                SessionProtocol.VERSION,
                UUID.randomUUID(),
                session.sessionId().value(),
                session.version(),
                clock.instant());
    }

    private StompFrameHandler bytesHandler(BlockingQueue<byte[]> frames) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((byte[]) payload);
            }
        };
    }

    private JsonNode readFrame(BlockingQueue<byte[]> frames) throws Exception {
        byte[] payload = frames.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
    }

    private static final class RecordingSessionHandler extends StompSessionHandlerAdapter {

        private final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            failures.add(new IllegalStateException(
                    new String((byte[]) payload, StandardCharsets.UTF_8)));
        }

        @Override
        public void handleException(
                StompSession session,
                StompCommand command,
                StompHeaders headers,
                byte[] payload,
                Throwable exception) {
            failures.add(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failures.add(exception);
        }

        Throwable failure() {
            return failures.peek();
        }

        Throwable awaitFailure() throws InterruptedException {
            return failures.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    record ProbedSubscription(UUID accountId, String destination) {
    }

    static class SubscriptionProbe implements ApplicationListener<SessionSubscribeEvent> {

        private final BlockingQueue<ProbedSubscription> subscriptions =
                new LinkedBlockingQueue<>();

        @Override
        public void onApplicationEvent(SessionSubscribeEvent event) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Principal principal = accessor.getUser();
            if (principal != null && accessor.getDestination() != null) {
                subscriptions.add(new ProbedSubscription(
                        UUID.fromString(principal.getName()), accessor.getDestination()));
            }
        }

        ProbedSubscription poll(Duration timeout) throws InterruptedException {
            return subscriptions.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void clear() {
            subscriptions.clear();
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        SubscriptionProbe subscriptionProbe() {
            return new SubscriptionProbe();
        }
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(INITIAL_TIME);
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        void set(Instant instant) {
            current.set(instant);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}

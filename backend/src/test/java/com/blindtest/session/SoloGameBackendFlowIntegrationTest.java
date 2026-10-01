package com.blindtest.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.model.game.GameRandom;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.store.SessionStore;
import com.blindtest.testing.InMemoryMediaStorage;
import com.blindtest.websocket.protocol.SessionCommand;
import com.blindtest.websocket.protocol.SessionCommandType;
import com.blindtest.websocket.protocol.SessionProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end backend coverage for the solo game: REST session creation, real STOMP transport,
 * authoritative timers, reconnect snapshots, command idempotency, authorization, durable
 * PostgreSQL history completion, and the refresh-safe summary endpoint, all without a browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Import(SoloGameBackendFlowIntegrationTest.BackendFlowTestConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.session.cleanup-delay=PT10M"
})
class SoloGameBackendFlowIntegrationTest {

    private static final Instant START = Instant.parse("2026-09-02T10:00:00Z");
    // The full-stack test crosses the embedded server, the STOMP broker, and PostgreSQL.
    // Keep enough headroom for a loaded CI runner after reconnects and database writes.
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final int TRACK_COUNT = 5;
    private static final int ROUND_SECONDS = 30;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static WebSocketStompClient stompClient;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private SessionStore sessionStore;
    @Autowired
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private RecordingSessionScheduler scheduler;
    @Autowired
    private InMemoryMediaStorage mediaStorage;
    @Autowired
    private SubscriptionProbe subscriptionProbe;

    @MockBean
    private EmailSender emailSender;
    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private UserEntity owner;
    private UserEntity intruder;
    private PlaylistEntity playlist;

    @BeforeAll
    static void startClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterAll
    static void stopClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @BeforeEach
    void setUp() {
        clock.set(START);
        scheduler.clear();
        mediaStorage.clear();
        subscriptionProbe.clear();
        historyRepository.deleteAll();
        participantRepository.deleteAll();
        roundRepository.deleteAll();
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        owner = saveUser("flow-owner");
        intruder = saveUser("flow-intruder");
        playlist = playlistRepository.save(buildPlaylist());
        for (int index = 0; index < TRACK_COUNT; index++) {
            TrackEntity track = trackRepository.save(buildTrack(index));
            playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, index));
            seedMedia(track.getAudioObjectKey());
        }
    }

    @Test
    void completesFullSoloGameOverRestStompPostgresAndServesDurableSummary() throws Exception {
        UUID sessionId = createSessionViaRest();
        Member member = connectMember(owner, sessionId);

        send(member, sessionId, SessionCommandType.START_GAME, 0, null);
        awaitPublic(member, "ROUND_STARTED");
        assertThat(playbackUrl(member.lastStarted)).startsWith("https://media.example.test/");

        playCorrectTitleRound(member, sessionId, 0);
        playTimeoutRound(member, sessionId, 1);
        playSkippedRound(member, sessionId, 2);
        member = reconnectAndProbeRound(member, sessionId, 3);
        playSkippedRound(member, sessionId, 3);
        playDuplicateSkipRound(member, sessionId, 4);

        JsonNode finished = awaitPublic(member, "GAME_FINISHED");
        assertThat(finished.at("/payload/titlesFound").asInt()).isEqualTo(1);
        assertThat(finished.at("/payload/skippedRounds").asInt()).isEqualTo(3);
        assertThat(finished.at("/payload/timedOutRounds").asInt()).isEqualTo(1);
        member.session.disconnect();

        assertDurableHistoryPersisted(sessionId);
        assertOwnerSummaryIsServed(sessionId);
    }

    @Test
    void duplicateDeadlineTimerProducesOneRevealAcrossTheFullStack() throws Exception {
        UUID sessionId = createSessionViaRest();
        Member member = connectMember(owner, sessionId);
        send(member, sessionId, SessionCommandType.START_GAME, 0, null);
        awaitPublic(member, "ROUND_STARTED");
        long startedVersion = currentVersion(sessionId);

        RecordedTask deadline = scheduler.latest();
        clock.set(deadline.executeAt());
        runConcurrently(deadline.task(), deadline.task());

        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/outcome").asText()).isEqualTo("TIMEOUT");
        assertThat(revealed.at("/payload/streamingLinks/spotify").asText()).
                startsWith("https://open.spotify.com/track/flow-");
        assertThat(revealed.at("/payload/streamingLinks").size()).isOne();
        assertThat(revealed.at("/payload/coverUrl").asText()).matches("/api/covers/[0-9a-f-]{36}");
        assertThat(currentVersion(sessionId)).isEqualTo(startedVersion + 1);
        assertThat(drainPublicOfType(member, "ROUND_REVEALED")).isZero();
        member.session.disconnect();
    }

    @Test
    void answerRaceWithDeadlineTimerYieldsExactlyOneAuthoritativeOutcome() throws Exception {
        // E2E-06 (timer/answer race): a correct answer arriving at the exact instant the
        // authoritative deadline timer fires must resolve to exactly one outcome, never both —
        // a duplicate reveal would double-score the round and, if it reached completion, write
        // a duplicate round into durable history.
        UUID sessionId = createSessionViaRest();
        Member member = connectMember(owner, sessionId);
        send(member, sessionId, SessionCommandType.START_GAME, 0, null);
        awaitPublic(member, "ROUND_STARTED");
        long startedVersion = currentVersion(sessionId);
        String title = expectedAnswer(sessionId, 0, true);

        RecordedTask deadline = scheduler.latest();
        clock.set(deadline.executeAt());
        runConcurrently(deadline.task(), () -> sendUnchecked(member, sessionId, startedVersion, title));

        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/outcome").asText()).isIn("TITLE_FOUND", "TIMEOUT");
        assertThat(currentVersion(sessionId)).isEqualTo(startedVersion + 1);
        assertThat(drainPublicOfType(member, "ROUND_REVEALED")).isZero();
        member.session.disconnect();
    }

    @Test
    void nonMemberCannotObserveOrCommandAnotherOwnersSession() throws Exception {
        UUID sessionId = createSessionViaRest();

        RecordingSessionHandler subscribeHandler = new RecordingSessionHandler();
        StompSession stranger = connect(token(intruder), subscribeHandler);
        stranger.subscribe(
                SessionProtocol.publicEventDestination(sessionId),
                bytesSink(new LinkedBlockingQueue<>()));
        assertThat(subscribeHandler.awaitFailure()).isNotNull();

        RecordingSessionHandler commandHandler = new RecordingSessionHandler();
        StompSession commander = connect(token(intruder), commandHandler);
        StompHeaders headers = new StompHeaders();
        headers.setDestination(SessionProtocol.commandDestination(sessionId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        commander.send(headers, objectMapper.writeValueAsBytes(new SessionCommand(
                SessionProtocol.VERSION, UUID.randomUUID(), 0, SessionCommandType.START_GAME, null)));
        assertThat(commandHandler.awaitFailure()).isNotNull();

        assertThat(currentVersion(sessionId)).isZero();
    }

    private void playCorrectTitleRound(Member member, UUID sessionId, int roundIndex)
            throws Exception {
        clock.advance(Duration.ofSeconds(5));
        String title = expectedAnswer(sessionId, roundIndex, true);
        send(member, sessionId, SessionCommandType.SUBMIT_ANSWER, currentVersion(sessionId), title);
        awaitPublic(member, "SCORE_UPDATED");
        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/outcome").asText()).isEqualTo("TITLE_FOUND");
        assertThat(revealed.at("/payload/roundPoints").asInt()).isPositive();
        advanceToNextRound(member);
    }

    private void playTimeoutRound(Member member, UUID sessionId, int roundIndex) throws Exception {
        clock.advance(Duration.ofSeconds(3));
        String artist = expectedAnswer(sessionId, roundIndex, false);
        send(member, sessionId, SessionCommandType.SUBMIT_ANSWER, currentVersion(sessionId), artist);
        JsonNode secondary = awaitPrivate(member, "SECONDARY_MATCH");
        assertThat(secondary.at("/payload/answerType").asText()).isEqualTo("ARTIST");

        RecordedTask deadline = scheduler.latest();
        clock.set(deadline.executeAt());
        deadline.task().run();
        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/outcome").asText()).isEqualTo("TIMEOUT");
        advanceToNextRound(member);
    }

    private void playSkippedRound(Member member, UUID sessionId, int roundIndex) throws Exception {
        clock.advance(Duration.ofSeconds(2));
        send(member, sessionId, SessionCommandType.SKIP_ROUND, currentVersion(sessionId), null);
        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/roundIndex").asInt()).isEqualTo(roundIndex);
        assertThat(revealed.at("/payload/outcome").asText()).isEqualTo("SKIPPED");
        advanceToNextRound(member);
    }

    private void playDuplicateSkipRound(Member member, UUID sessionId, int roundIndex)
            throws Exception {
        clock.advance(Duration.ofSeconds(2));
        UUID commandId = UUID.randomUUID();
        long version = currentVersion(sessionId);
        sendWithId(member, sessionId, SessionCommandType.SKIP_ROUND, version, null, commandId);
        JsonNode revealed = awaitPublic(member, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/roundIndex").asInt()).isEqualTo(roundIndex);

        sendWithId(member, sessionId, SessionCommandType.SKIP_ROUND, version, null, commandId);
        JsonNode error = awaitPrivate(member, "ERROR");
        assertThat(error.at("/payload/code").asText()).isEqualTo("DUPLICATE_COMMAND");

        RecordedTask advance = scheduler.latest();
        clock.set(advance.executeAt());
        advance.task().run();
    }

    private Member reconnectAndProbeRound(Member previous, UUID sessionId, int roundIndex)
            throws Exception {
        previous.session.disconnect();
        Member member = connectMember(owner, sessionId);
        send(member, sessionId, SessionCommandType.REQUEST_SNAPSHOT, 0, null);
        JsonNode snapshot = awaitPrivate(member, "CURRENT_SNAPSHOT");
        assertThat(snapshot.at("/payload/gameStatus").asText()).isEqualTo("PLAYING");
        assertThat(snapshot.at("/payload/currentRoundIndex").asInt()).isEqualTo(roundIndex);
        assertThat(snapshot.at("/payload/currentRound/status").asText()).isEqualTo("PLAYING");
        assertThat(snapshot.at("/payload/currentRound/playbackUrl").asText()).
                startsWith("https://media.example.test/");
        return member;
    }

    private void advanceToNextRound(Member member) throws Exception {
        RecordedTask advance = scheduler.latest();
        clock.set(advance.executeAt());
        advance.task().run();
        awaitPublic(member, "ROUND_STARTED");
    }

    private void assertDurableHistoryPersisted(UUID sessionId) {
        assertThat(sessionStore.find(new SessionId(sessionId), clock.instant())).isEmpty();
        var history = historyRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(history.getGameMode()).isEqualTo("SOLO");
        assertThat(participantRepository.findByGameHistoryId(history.getId())).hasSize(1);
        assertThat(roundRepository.findByGameHistoryIdOrderByRoundIndex(history.getId())).
                extracting(round -> round.getOutcome().name()).
                containsExactly("TITLE_FOUND", "TIMEOUT", "SKIPPED", "SKIPPED", "SKIPPED");
    }

    private void assertOwnerSummaryIsServed(UUID sessionId) throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", sessionId).
                        header(HttpHeaders.AUTHORIZATION, bearer(owner))).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalRounds").value(TRACK_COUNT)).
                andExpect(jsonPath("$.titlesFound").value(1)).
                andExpect(jsonPath("$.skippedRounds").value(3)).
                andExpect(jsonPath("$.timedOutRounds").value(1)).
                andExpect(jsonPath("$.rounds.length()").value(TRACK_COUNT)).
                andExpect(jsonPath("$.rounds[0].outcome").value("TITLE_FOUND")).
                andExpect(jsonPath("$.rounds[1].outcome").value("TIMEOUT")).
                andExpect(jsonPath("$.rounds[0].audioObjectKey").doesNotExist()).
                andExpect(jsonPath("$.rounds[0].youtubeUrl").doesNotExist());

        mockMvc.perform(get("/api/sessions/{sessionId}/summary", sessionId).
                        header(HttpHeaders.AUTHORIZATION, bearer(intruder))).
                andExpect(status().isNotFound());
    }

    private UUID createSessionViaRest() throws Exception {
        String body = "{\"playlistId\":\"" + playlist.getId() + "\",\"trackCount\":" + TRACK_COUNT
                + ",\"maxRoundSeconds\":" + ROUND_SECONDS + ",\"extensions\":{}}";
        MvcResult result = mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(owner)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(body)).
                andExpect(status().isCreated()).
                andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsByteArray()).get("sessionId").asText());
    }

    private Member connectMember(UserEntity account, UUID sessionId) throws Exception {
        StompSession session = connect(token(account), new RecordingSessionHandler());
        Member member = new Member(session);
        subscribeAndAwait(session, SessionProtocol.publicEventDestination(sessionId), member.publicFrames);
        subscribeAndAwait(
                session, SessionProtocol.privateEventDestination(sessionId), member.privateFrames);
        return member;
    }

    private StompSession connect(String token, RecordingSessionHandler handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync(
                        "ws://127.0.0.1:" + port + SessionProtocol.WEB_SOCKET_ENDPOINT,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        handler).
                get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private void subscribeAndAwait(
            StompSession session,
            String destination,
            BlockingQueue<JsonNode> sink) throws InterruptedException {
        session.subscribe(destination, bytesSink(sink));
        assertThat(subscriptionProbe.awaitDestination(destination, WAIT)).isTrue();
    }

    private void send(
            Member member,
            UUID sessionId,
            SessionCommandType type,
            long version,
            String answer) throws Exception {
        sendWithId(member, sessionId, type, version, answer, UUID.randomUUID());
    }

    private void sendUnchecked(Member member, UUID sessionId, long version, String answer) {
        try {
            send(member, sessionId, SessionCommandType.SUBMIT_ANSWER, version, answer);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void sendWithId(
            Member member,
            UUID sessionId,
            SessionCommandType type,
            long version,
            String answer,
            UUID commandId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(SessionProtocol.commandDestination(sessionId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        SessionCommand command = new SessionCommand(
                SessionProtocol.VERSION, commandId, version, type, answer);
        member.session.send(headers, objectMapper.writeValueAsBytes(command));
    }

    private JsonNode awaitPublic(Member member, String type) throws InterruptedException {
        JsonNode event = awaitEvent(member.publicFrames, type);
        if (type.equals("ROUND_STARTED")) {
            member.lastStarted = event;
        }
        return event;
    }

    private JsonNode awaitPrivate(Member member, String type) throws InterruptedException {
        return awaitEvent(member.privateFrames, type);
    }

    private JsonNode awaitEvent(BlockingQueue<JsonNode> frames, String type)
            throws InterruptedException {
        Instant limit = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(limit)) {
            JsonNode node = frames.poll(200, TimeUnit.MILLISECONDS);
            if (node != null && node.get("type").asText().equals(type)) {
                return node;
            }
        }
        throw new AssertionError("Did not receive a " + type + " event within " + WAIT);
    }

    private int drainPublicOfType(Member member, String type) {
        int count = 0;
        JsonNode node;
        while ((node = member.publicFrames.poll()) != null) {
            if (node.get("type").asText().equals(type)) {
                count++;
            }
        }
        return count;
    }

    private String playbackUrl(JsonNode roundStarted) {
        return roundStarted.at("/payload/playbackUrl").asText();
    }

    private String expectedAnswer(UUID sessionId, int roundIndex, boolean title) {
        ActiveSessionSnapshot snapshot = sessionStore.find(
                new SessionId(sessionId), clock.instant()).orElseThrow();
        var answers = snapshot.gameState().trackSelection().tracks().get(roundIndex).answers();
        return title ? answers.title() : answers.artist();
    }

    private long currentVersion(UUID sessionId) {
        return sessionStore.find(new SessionId(sessionId), clock.instant()).orElseThrow().version();
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> firstResult = executor.submit(() -> awaitAndRun(ready, go, first));
            Future<?> secondResult = executor.submit(() -> awaitAndRun(ready, go, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            firstResult.get(5, TimeUnit.SECONDS);
            secondResult.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitAndRun(CountDownLatch ready, CountDownLatch go, Runnable task) {
        try {
            ready.countDown();
            go.await();
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private StompFrameHandler bytesSink(BlockingQueue<JsonNode> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    sink.add(objectMapper.readTree((byte[]) payload));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        };
    }

    private void seedMedia(String objectKey) {
        try {
            Path file = Files.createTempFile("blindtest-media", ".mp3");
            Files.write(file, new byte[] {'I', 'D', '3', 0, 0, 0});
            mediaStorage.store(objectKey, file, "audio/mpeg");
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private UserEntity saveUser(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private PlaylistEntity buildPlaylist() {
        PlaylistEntity entity = new PlaylistEntity();
        entity.setName("Backend flow playlist");
        entity.setCreatedBy(owner);
        return entity;
    }

    private TrackEntity buildTrack(int index) {
        TrackEntity track = new TrackEntity();
        track.setTitle("Flow Title " + index);
        track.setArtist("Flow Artist " + index);
        track.setComposer("Flow Composer " + index);
        track.setAlbum("Flow Album " + index);
        track.setAudioObjectKey("tracks/flow-" + index + "/audio.mp3");
        track.setSpotifyUrl("https://open.spotify.com/track/flow-" + index);
        track.setCoverId(UUID.nameUUIDFromBytes(("cover-" + index).getBytes(StandardCharsets.UTF_8)));
        track.setCoverSource(CoverSource.UPLOAD);
        track.setAudioChecksum("checksum-" + index);
        track.setAudioSizeBytes(2048L + index);
        track.setDurationSeconds(240);
        track.setExcerptStartSeconds(index);
        track.setStatus(TrackStatus.PUBLISHED);
        return track;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + token(user);
    }

    private String token(UserEntity user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), UserRole.USER);
    }

    private static final class Member {

        private final StompSession session;
        private final BlockingQueue<JsonNode> publicFrames = new LinkedBlockingQueue<>();
        private final BlockingQueue<JsonNode> privateFrames = new LinkedBlockingQueue<>();
        private JsonNode lastStarted;

        private Member(StompSession session) {
            this.session = session;
        }
    }

    private static final class RecordingSessionHandler extends StompSessionHandlerAdapter {

        private final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();

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

        private Throwable awaitFailure() throws InterruptedException {
            return failures.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    static final class SubscriptionProbe implements ApplicationListener<SessionSubscribeEvent> {

        private final BlockingQueue<String> destinations = new LinkedBlockingQueue<>();

        @Override
        public void onApplicationEvent(SessionSubscribeEvent event) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Principal principal = accessor.getUser();
            if (principal != null && accessor.getDestination() != null) {
                destinations.add(accessor.getDestination());
            }
        }

        boolean awaitDestination(String destination, Duration timeout) throws InterruptedException {
            Instant limit = Instant.now().plus(timeout);
            while (Instant.now().isBefore(limit)) {
                String seen = destinations.poll(200, TimeUnit.MILLISECONDS);
                if (destination.equals(seen)) {
                    return true;
                }
            }
            return false;
        }

        void clear() {
            destinations.clear();
        }
    }

    private record RecordedTask(Instant executeAt, Runnable task) {
    }

    static final class RecordingSessionScheduler implements SessionTaskScheduler {

        private final List<RecordedTask> tasks = new CopyOnWriteArrayList<>();

        @Override
        public void schedule(Instant executeAt, Runnable task) {
            tasks.add(new RecordedTask(executeAt, task));
        }

        @Override
        public void schedule(SessionId sessionId, Instant executeAt, Runnable task) {
            tasks.add(new RecordedTask(executeAt, task));
        }

        @Override
        public int cancelSessionTasks(SessionId sessionId) {
            return 0;
        }

        RecordedTask latest() {
            return tasks.get(tasks.size() - 1);
        }

        void clear() {
            tasks.clear();
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
            current.updateAndGet(value -> value.plus(duration));
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

    @TestConfiguration(proxyBeanMethods = false)
    static class BackendFlowTestConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        RecordingSessionScheduler recordingSessionScheduler() {
            return new RecordingSessionScheduler();
        }

        @Bean
        @Primary
        InMemoryMediaStorage flowMediaStorage() {
            return new InMemoryMediaStorage();
        }

        @Bean
        @Primary
        GameRandom flowGameRandom() {
            return bound -> 0;
        }

        @Bean
        SubscriptionProbe subscriptionProbe() {
            return new SubscriptionProbe();
        }
    }
}

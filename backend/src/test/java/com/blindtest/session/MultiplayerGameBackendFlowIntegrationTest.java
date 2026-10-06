package com.blindtest.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
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
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.session.SoloGameBackendFlowIntegrationTest.MutableClock;
import com.blindtest.session.SoloGameBackendFlowIntegrationTest.RecordedTask;
import com.blindtest.session.SoloGameBackendFlowIntegrationTest.RecordingSessionScheduler;
import com.blindtest.session.SoloGameBackendFlowIntegrationTest.SubscriptionProbe;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MP-03 acceptance: three real STOMP clients play a lobby-started game with mixed finds, a
 * skip vote, and a timeout; every score reconciles and no private answer leaks.
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
class MultiplayerGameBackendFlowIntegrationTest {

    private static final Instant START = Instant.parse("2026-10-02T18:00:00Z");
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Duration LEAD = Duration.ofMillis(1500);
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
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private SessionStore sessionStore;
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

    private PlaylistEntity playlist;
    private UUID sessionId;
    private Player a;
    private Player b;
    private Player c;
    private List<Player> players;
    private final Map<Player, Integer> expectedTotals = new HashMap<>();
    private final Map<Player, UserEntity> users = new HashMap<>();
    private long staleVersion;

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
    }

    @Test
    void threePlayersPlayAFullGameWithFindsASkipVoteAndATimeout() throws Exception {
        Instant startsAt = startLobbyGame();
        startsAt = playEveryoneFindsRound(startsAt);
        startsAt = playSkipVoteRound(startsAt);
        startsAt = playTimeoutRound(startsAt);
        playQuickSkipsAndFinish(startsAt);

        JsonNode standings = awaitAll(players, "GAME_FINISHED").at("/payload/standings");
        assertThat(standings).hasSize(3);
        assertThat(standings.get(0).get("participantId").asText()).isEqualTo(c.participantId);
        assertThat(standings.get(0).get("totalScore").asInt()).isEqualTo(expectedTotals.get(c));
        assertThat(standings.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(standings.get(1).get("participantId").asText()).isEqualTo(a.participantId);
        assertThat(standings.get(2).get("participantId").asText()).isEqualTo(b.participantId);
        assertThat(standings.get(2).get("titlesFound").asInt()).isEqualTo(2);
        assertThat(expectedTotals).containsEntry(a, 833).containsEntry(b, 767).containsEntry(c, 1300);

        assertNoAnswerOrDiscoveryLeaked();
        players.forEach(player -> player.session.disconnect());
        assertMultiplayerHistoryAndSummaries();
    }

    /** MP-08: a guest joins with a nickname, plays over STOMP, and reads its own summary. */
    @Test
    void registeredHostAndGuestPlayToTheGuestsSummary() throws Exception {
        org.mockito.Mockito.when(turnstileVerifier.verify("solved")).thenReturn(true);
        UserEntity alice = saveUser("mp-alice");
        seedPlaylist(alice);
        JsonNode lobby = json(perform(post("/api/lobbies"), alice, configuration()));
        String code = lobby.get("code").asText();
        sessionId = UUID.fromString(lobby.get("sessionId").asText());
        a = connect(alice, lobby.get("participantId").asText());
        JsonNode joined = json(mockMvc.perform(post("/api/lobbies/" + code + "/guests").
                contentType(MediaType.APPLICATION_JSON).
                content("{\"nickname\":\"Visitor\",\"turnstileToken\":\"solved\"}")).
                andExpect(status().isOk()));
        String guestToken = joined.get("guestToken").asText();
        Player guest = connect(guestToken, joined.at("/lobby/participantId").asText());
        mockMvc.perform(put("/api/lobbies/" + code + "/participants/me/ready").
                        header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"ready\":true}")).
                andExpect(status().isOk());
        perform(post("/api/lobbies/" + code + "/start"), alice, null).andExpect(status().isOk());
        players = List.of(a, guest);
        players.forEach(player -> expectedTotals.put(player, 0));
        awaitAll(players, "LOBBY_STARTED");

        send(a, SessionCommandType.START_GAME, version(), null);
        Instant startsAt = Instant.parse(
                awaitAll(players, "ROUND_STARTED").at("/payload/startsAt").asText());
        // Private events reach the guest's own queue.
        send(guest, SessionCommandType.REQUEST_SNAPSHOT, version(), null);
        assertThat(awaitPrivate(guest, "CURRENT_SNAPSHOT").get("recipientParticipantId").asText()).
                isEqualTo(guest.participantId);
        clock.set(startsAt.plusSeconds(5));
        send(guest, SessionCommandType.SUBMIT_ANSWER, version(), title(0));
        assertScore(guest, 833);
        clock.set(startsAt.plusSeconds(10));
        send(a, SessionCommandType.SUBMIT_ANSWER, version(), title(0));
        assertScore(a, 667);
        awaitAll(players, "ROUND_REVEALED");
        startsAt = advance();
        for (int round = 1; round < TRACK_COUNT; round++) {
            clock.set(startsAt.plusSeconds(2));
            send(a, SessionCommandType.SKIP_ROUND, version(), null);
            awaitAll(players, "SKIP_VOTE_UPDATED");
            send(guest, SessionCommandType.SKIP_ROUND, version(), null);
            awaitAll(players, "ROUND_REVEALED");
            if (round < TRACK_COUNT - 1) {
                startsAt = advance();
            }
        }
        send(a, SessionCommandType.ADVANCE_ROUND, version(), null);
        awaitAll(players, "GAME_FINISHED");
        players.forEach(player -> player.session.disconnect());

        GameHistoryEntity history = awaitHistory();
        var stored = participantRepository.findByGameHistoryId(history.getId()).stream().
                filter(participant -> participant.getParticipantId().toString().
                        equals(guest.participantId)).
                findFirst().orElseThrow();
        assertThat(stored.getUser()).isNull();
        assertThat(stored.getParticipantKind().name()).isEqualTo("GUEST");
        assertThat(stored.getDisplayName()).isEqualTo("Visitor");
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", sessionId).
                        header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalScore").value(833)).
                andExpect(jsonPath("$.standings[0].participantId").value(guest.participantId)).
                andExpect(jsonPath("$.standings[0].guest").value(true));
    }

    /** MP-04: every participant is stored with a shared rank and reads their own summary. */
    private void assertMultiplayerHistoryAndSummaries() throws Exception {
        GameHistoryEntity history = awaitHistory();
        assertThat(history.getGameMode()).isEqualTo("MULTIPLAYER");
        assertThat(history.getTotalScore()).isEqualTo(833 + 767 + 1300);
        Map<String, Integer> ranks = new HashMap<>();
        participantRepository.findByGameHistoryId(history.getId()).forEach(participant ->
                ranks.put(participant.getParticipantId().toString(), participant.getFinalRank()));
        assertThat(ranks).containsExactlyInAnyOrderEntriesOf(Map.of(
                c.participantId, 1, a.participantId, 2, b.participantId, 3));
        var rounds = roundRepository.findByGameHistoryIdOrderByRoundIndex(history.getId());
        assertThat(rounds).extracting(round -> round.getOutcome().name()).
                containsExactly("TITLE_FOUND", "SKIPPED", "TIMEOUT", "SKIPPED", "SKIPPED");
        assertThat(rounds.getFirst().getPoints()).isEqualTo(833 + 667 + 500);

        JsonNode bobSummary = json(perform(
                get("/api/sessions/{sessionId}/summary", sessionId), users.get(b), null).
                andExpect(status().isOk()));
        assertThat(bobSummary.get("gameMode").asText()).isEqualTo("MULTIPLAYER");
        assertThat(bobSummary.get("participantId").asText()).isEqualTo(b.participantId);
        assertThat(bobSummary.get("totalScore").asInt()).isEqualTo(767);
        assertThat(bobSummary.get("titlesFound").asInt()).isEqualTo(2);
        assertThat(bobSummary.at("/rounds/0/points").asInt()).isEqualTo(667);
        assertThat(bobSummary.at("/rounds/1/titleFound").asBoolean()).isFalse();
        assertThat(bobSummary.at("/rounds/2/points").asInt()).isEqualTo(100);
        assertThat(bobSummary.at("/standings/0/displayName").asText()).isEqualTo("mp-carol");
        assertThat(bobSummary.at("/standings/2/rank").asInt()).isEqualTo(3);
        JsonNode carolSummary = json(perform(
                get("/api/sessions/{sessionId}/summary", sessionId), users.get(c), null));
        assertThat(carolSummary.get("totalScore").asInt()).isEqualTo(1300);

        perform(get("/api/sessions/{sessionId}/summary", sessionId), saveUser("mp-stranger"), null).
                andExpect(status().isNotFound());
        perform(get("/api/sessions/{sessionId}/summary", UUID.randomUUID()), users.get(a), null).
                andExpect(status().isNotFound());
    }

    private GameHistoryEntity awaitHistory() throws InterruptedException {
        Instant limit = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(limit)) {
            var history = historyRepository.findBySessionId(sessionId);
            if (history.isPresent()) {
                return history.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Multiplayer history was not persisted within " + WAIT);
    }

    /** Bob and Carol join by code and ready up; everyone subscribes before the host starts. */
    private Instant startLobbyGame() throws Exception {
        UserEntity alice = saveUser("mp-alice");
        UserEntity bob = saveUser("mp-bob");
        UserEntity carol = saveUser("mp-carol");
        seedPlaylist(alice);
        JsonNode lobby = json(perform(post("/api/lobbies"), alice, configuration()));
        String code = lobby.get("code").asText();
        sessionId = UUID.fromString(lobby.get("sessionId").asText());
        a = connect(alice, lobby.get("participantId").asText());
        users.put(a, alice);
        b = connect(bob, json(perform(
                post("/api/lobbies/" + code + "/participants"), bob, null)).get("participantId").asText());
        c = connect(carol, json(perform(
                post("/api/lobbies/" + code + "/participants"), carol, null)).get("participantId").asText());
        for (UserEntity member : List.of(bob, carol)) {
            perform(put("/api/lobbies/" + code + "/participants/me/ready"), member, "{\"ready\":true}").
                    andExpect(status().isOk());
        }
        perform(post("/api/lobbies/" + code + "/start"), alice, null).andExpect(status().isOk());
        users.put(b, bob);
        users.put(c, carol);
        players = List.of(a, b, c);
        players.forEach(player -> expectedTotals.put(player, 0));
        awaitAll(players, "LOBBY_STARTED");

        send(a, SessionCommandType.START_GAME, version(), null);
        JsonNode started = awaitAll(players, "ROUND_STARTED");
        Instant startsAt = Instant.parse(started.at("/payload/startsAt").asText());
        // Multiplayer rounds start after a short lead so every client begins playback together.
        assertThat(startsAt).isEqualTo(clock.instant().plus(LEAD));
        return startsAt;
    }

    /** All three find the title; scores come from the shared start with no first bonus. */
    private Instant playEveryoneFindsRound(Instant startsAt) throws Exception {
        clock.set(startsAt.plusSeconds(5));
        long roundStartVersion = version();
        send(a, SessionCommandType.SUBMIT_ANSWER, version(), title(0));
        assertScore(a, 833);
        clock.set(startsAt.plusSeconds(10));
        // Bob still names the round-start version: other players' commands do not make it stale.
        send(b, SessionCommandType.SUBMIT_ANSWER, roundStartVersion, title(0));
        assertScore(b, 667);
        clock.set(startsAt.plusSeconds(11));
        send(b, SessionCommandType.SUBMIT_ANSWER, version(), title(0));
        assertThat(awaitPrivate(b, "ANSWER_REJECTED").at("/payload/reason").asText()).
                isEqualTo("ALREADY_FOUND");
        clock.set(startsAt.plusSeconds(15));
        send(c, SessionCommandType.SUBMIT_ANSWER, version(), title(0));
        assertScore(c, 500);
        JsonNode revealed = awaitAll(players, "ROUND_REVEALED");
        assertThat(revealed.at("/payload/outcome").asText()).isEqualTo("TITLE_FOUND");
        assertRevealScores(revealed);
        staleVersion = version();
        return advance();
    }

    /** A private artist discovery, one find, then two of three skip votes. */
    private Instant playSkipVoteRound(Instant startsAt) throws Exception {
        clock.set(startsAt.plusSeconds(3));
        send(a, SessionCommandType.SUBMIT_ANSWER, version(), artist(1));
        assertThat(awaitPrivate(a, "SECONDARY_MATCH").at("/payload/answerType").asText()).
                isEqualTo("ARTIST");
        // A command sent before this round started belongs to the previous round.
        send(c, SessionCommandType.SUBMIT_ANSWER, staleVersion, title(1));
        assertThat(awaitPrivate(c, "ANSWER_REJECTED").at("/payload/reason").asText()).
                isEqualTo("STALE_SESSION_VERSION");
        clock.set(startsAt.plusSeconds(6));
        send(c, SessionCommandType.SUBMIT_ANSWER, version(), title(1));
        assertScore(c, 800);
        send(a, SessionCommandType.SKIP_ROUND, version(), null);
        JsonNode vote = awaitAll(players, "SKIP_VOTE_UPDATED");
        assertThat(vote.at("/payload/votes").asInt()).isEqualTo(1);
        assertThat(vote.at("/payload/requiredVotes").asInt()).isEqualTo(2);
        send(b, SessionCommandType.SKIP_ROUND, version(), null);
        JsonNode skipped = awaitAll(players, "ROUND_REVEALED");
        assertThat(skipped.at("/payload/outcome").asText()).isEqualTo("SKIPPED");
        assertRevealScores(skipped);
        return advance();
    }

    /** Bob finds it late, then the deadline reveals a timeout that keeps his points. */
    private Instant playTimeoutRound(Instant startsAt) throws Exception {
        clock.set(startsAt.plusSeconds(27));
        send(b, SessionCommandType.SUBMIT_ANSWER, version(), title(2));
        assertScore(b, 100);
        RecordedTask deadline = scheduler.latest();
        assertThat(deadline.executeAt()).isEqualTo(startsAt.plusSeconds(ROUND_SECONDS));
        clock.set(deadline.executeAt());
        deadline.task().run();
        JsonNode timedOut = awaitAll(players, "ROUND_REVEALED");
        assertThat(timedOut.at("/payload/outcome").asText()).isEqualTo("TIMEOUT");
        assertRevealScores(timedOut);
        return advance();
    }

    /** Rounds 3 and 4 end by skip vote; only the host may advance the last reveal early. */
    private void playQuickSkipsAndFinish(Instant firstStartsAt) throws Exception {
        Instant startsAt = firstStartsAt;
        for (int round = 3; round < TRACK_COUNT; round++) {
            clock.set(startsAt.plusSeconds(2));
            send(b, SessionCommandType.SKIP_ROUND, version(), null);
            awaitAll(players, "SKIP_VOTE_UPDATED");
            send(c, SessionCommandType.SKIP_ROUND, version(), null);
            awaitAll(players, "ROUND_REVEALED");
            if (round < TRACK_COUNT - 1) {
                startsAt = advance();
            }
        }
        send(b, SessionCommandType.ADVANCE_ROUND, version(), null);
        assertThat(awaitPrivate(b, "ERROR").at("/payload/code").asText()).
                isEqualTo("SESSION_OWNER_REQUIRED");
        send(a, SessionCommandType.ADVANCE_ROUND, version(), null);
    }

    private void assertScore(Player scorer, int points) throws Exception {
        expectedTotals.merge(scorer, points, Integer::sum);
        for (Player player : players) {
            JsonNode score = awaitPublic(player, "SCORE_UPDATED");
            assertThat(score.at("/payload/participantId").asText()).isEqualTo(scorer.participantId);
            assertThat(score.at("/payload/roundPoints").asInt()).isEqualTo(points);
            assertThat(score.at("/payload/totalScore").asInt()).isEqualTo(expectedTotals.get(scorer));
        }
    }

    private void assertRevealScores(JsonNode revealed) {
        JsonNode scores = revealed.at("/payload/scores");
        assertThat(scores).hasSize(players.size());
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            assertThat(scores.get(index).get("participantId").asText()).isEqualTo(player.participantId);
            assertThat(scores.get(index).get("totalScore").asInt()).isEqualTo(expectedTotals.get(player));
        }
    }

    private void assertNoAnswerOrDiscoveryLeaked() {
        List<String> titles = new ArrayList<>();
        for (int round = 0; round < TRACK_COUNT; round++) {
            titles.add(title(round));
        }
        for (Player player : List.of(a, b, c)) {
            for (JsonNode event : player.publicLog) {
                String type = event.get("type").asText();
                if (!type.equals("ROUND_REVEALED")) {
                    // Only a reveal may name a title; finds are announced without the answer.
                    titles.forEach(title -> assertThat(event.toString()).doesNotContain(title));
                }
            }
            for (JsonNode event : player.privateLog) {
                assertThat(event.get("recipientParticipantId").asText()).isEqualTo(player.participantId);
            }
        }
        assertThat(b.privateLog).noneMatch(event -> event.get("type").asText().equals("SECONDARY_MATCH"));
        assertThat(c.privateLog).noneMatch(event -> event.get("type").asText().equals("SECONDARY_MATCH"));
    }

    private Instant advance() throws Exception {
        RecordedTask advance = scheduler.latest();
        clock.set(advance.executeAt());
        advance.task().run();
        JsonNode started = awaitAll(players, "ROUND_STARTED");
        return Instant.parse(started.at("/payload/startsAt").asText());
    }

    private JsonNode awaitAll(List<Player> players, String type) throws InterruptedException {
        JsonNode first = null;
        for (Player player : players) {
            JsonNode event = awaitPublic(player, type);
            if (first == null) {
                first = event;
            } else {
                assertThat(event.get("payload")).isEqualTo(first.get("payload"));
            }
        }
        return first;
    }

    private JsonNode awaitPublic(Player player, String type) throws InterruptedException {
        return awaitEvent(player.publicFrames, type);
    }

    private JsonNode awaitPrivate(Player player, String type) throws InterruptedException {
        return awaitEvent(player.privateFrames, type);
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

    private Player connect(UserEntity account, String participantId) throws Exception {
        return connect(token(account), participantId);
    }

    private Player connect(String bearerToken, String participantId) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bearerToken);
        StompSession session = stompClient.connectAsync(
                        "ws://127.0.0.1:" + port + SessionProtocol.WEB_SOCKET_ENDPOINT,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() { }).
                get(WAIT.toSeconds(), TimeUnit.SECONDS);
        Player player = new Player(session, participantId);
        subscribe(session, SessionProtocol.publicEventDestination(sessionId),
                player.publicFrames, player.publicLog);
        subscribe(session, SessionProtocol.privateEventDestination(sessionId),
                player.privateFrames, player.privateLog);
        return player;
    }

    private void subscribe(
            StompSession session,
            String destination,
            BlockingQueue<JsonNode> sink,
            List<JsonNode> log) throws InterruptedException {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    JsonNode node = objectMapper.readTree((byte[]) payload);
                    log.add(node);
                    sink.add(node);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        });
        assertThat(subscriptionProbe.awaitDestination(destination, WAIT)).isTrue();
    }

    private void send(Player player, SessionCommandType type, long version, String answer)
            throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(SessionProtocol.commandDestination(sessionId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        player.session.send(headers, objectMapper.writeValueAsBytes(new SessionCommand(
                SessionProtocol.VERSION, UUID.randomUUID(), version, type, answer)));
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            MockHttpServletRequestBuilder request,
            UserEntity user,
            String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long version() {
        return snapshot().version();
    }

    private String title(int round) {
        return snapshot().gameState().trackSelection().tracks().get(round).answers().title();
    }

    private String artist(int round) {
        return snapshot().gameState().trackSelection().tracks().get(round).answers().artist();
    }

    private ActiveSessionSnapshot snapshot() {
        return sessionStore.find(new SessionId(sessionId), clock.instant()).orElseThrow();
    }

    private String configuration() {
        return "{\"playlistId\":\"" + playlist.getId() + "\",\"trackCount\":" + TRACK_COUNT
                + ",\"maxRoundSeconds\":" + ROUND_SECONDS + ",\"extensions\":{}}";
    }

    private void seedPlaylist(UserEntity owner) {
        PlaylistEntity entity = new PlaylistEntity();
        entity.setName("Multiplayer flow playlist");
        entity.setCreatedBy(owner);
        playlist = playlistRepository.save(entity);
        for (int index = 0; index < TRACK_COUNT; index++) {
            TrackEntity track = new TrackEntity();
            track.setTitle("Party Title " + index);
            track.setArtist("Party Artist " + index);
            track.setAudioObjectKey("tracks/party-" + index + "/audio.mp3");
            track.setAudioChecksum("checksum-" + index);
            track.setAudioSizeBytes(2048L + index);
            track.setDurationSeconds(240);
            track.setExcerptStartSeconds(index);
            track.setStatus(TrackStatus.PUBLISHED);
            track = trackRepository.save(track);
            playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, index));
            seedMedia(track.getAudioObjectKey());
        }
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

    private String token(UserEntity user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), UserRole.USER);
    }

    private static final class Player {

        private final StompSession session;
        private final String participantId;
        private final BlockingQueue<JsonNode> publicFrames = new LinkedBlockingQueue<>();
        private final BlockingQueue<JsonNode> privateFrames = new LinkedBlockingQueue<>();
        private final List<JsonNode> publicLog = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<JsonNode> privateLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        private Player(StompSession session, String participantId) {
            this.session = session;
            this.participantId = participantId;
        }
    }
}

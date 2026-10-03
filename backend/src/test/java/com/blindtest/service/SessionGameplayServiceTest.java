package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.SessionProperties;
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
import com.blindtest.model.game.RoundOutcome;
import com.blindtest.model.game.RoundStatus;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.InMemorySessionStore;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.SessionEventPublisher;
import com.blindtest.websocket.protocol.AnswerRejectionReason;
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
import java.net.URI;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionGameplayServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T14:00:00Z");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PARTICIPANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID SESSION_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID GUEST_ACCOUNT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID GUEST_PARTICIPANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000005");
    private static final UUID MULTIPLAYER_SESSION_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000006");

    private final SessionStore store = new InMemorySessionStore();
    private final MutableClock clock = new MutableClock(NOW);
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final List<PublicSessionEvent> publicEvents = new CopyOnWriteArrayList<>();
    private final List<PrivateSessionEvent> privateEvents = new CopyOnWriteArrayList<>();

    private SessionGameplayService service;
    private MediaStorage mediaStorage;
    private GameplayRateLimitMetrics rateLimitMetrics;
    private GameHistoryCompletionService historyCompletionService;

    @BeforeEach
    void setUp() {
        SessionEventPublisher publisher = mock(SessionEventPublisher.class);
        doAnswer(invocation -> {
            PublicSessionEvent event = invocation.getArgument(0);
            assertPersisted(event.metadata());
            publicEvents.add(event);
            return null;
        }).when(publisher).publishPublic(any(PublicSessionEvent.class));
        doAnswer(invocation -> {
            PrivateSessionEvent event = invocation.getArgument(1);
            assertPersisted(event.metadata());
            privateEvents.add(event);
            return null;
        }).when(publisher).publishPrivate(any(UUID.class), any(PrivateSessionEvent.class));
        AtomicLong identity = new AtomicLong(100);
        SessionIdentityGenerator identities = () -> new UUID(0, identity.incrementAndGet());
        AtomicLong signature = new AtomicLong();
        mediaStorage = mock(MediaStorage.class);
        when(mediaStorage.presignedGetUrl(anyString(), any(Duration.class))).thenAnswer(invocation ->
                URI.create("https://media.example.test/audio.mp3?signature="
                        + signature.incrementAndGet()));
        rateLimitMetrics = mock(GameplayRateLimitMetrics.class);
        GameplayCommandRateLimiter rateLimiter =
                new GameplayCommandRateLimiter(rateLimitMetrics);
        SessionExpirationPolicy expirationPolicy = new SessionExpirationPolicy(
                new SessionProperties(
                        Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofMinutes(1)),
                clock);
        historyCompletionService = mock(GameHistoryCompletionService.class);
        service = new SessionGameplayService(
                store, scheduler, expirationPolicy, mediaStorage, rateLimiter,
                historyCompletionService,
                publisher, identities);
        assertThat(store.create(createdSession(), clock.instant())).
                isEqualTo(SessionCreateOutcome.CREATED);
    }

    @Test
    void startsRoundPersistsEachVersionBeforePublishingAndSchedulesDeadline() {
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.START_GAME, 0, null, UUID.randomUUID()));

        ActiveSessionSnapshot state = current();
        assertThat(state.version()).isEqualTo(2);
        assertThat(state.gameState().status()).isEqualTo(GameStatus.PLAYING);
        assertThat(state.gameState().currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PLAYING);
        assertThat(state.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(publicEvents).extracting(PublicSessionEvent::type).
                containsExactly(
                        PublicSessionEventType.ROUND_PREPARING,
                        PublicSessionEventType.ROUND_STARTED);
        PublicSessionEventPayload.RoundStarted started =
                (PublicSessionEventPayload.RoundStarted) publicEvents.get(1).payload();
        assertThat(started.playbackUrl()).contains(
                URI.create("https://media.example.test/audio.mp3?signature=1"));
        assertThat(started.startsAt()).isEqualTo(NOW);
        assertThat(started.deadline()).isEqualTo(NOW.plusSeconds(30));
        assertThat(started.excerptStartSeconds()).isZero();
        ArgumentCaptor<Duration> validity = ArgumentCaptor.forClass(Duration.class);
        verify(mediaStorage).presignedGetUrl(eq("tracks/0/audio.mp3"), validity.capture());
        assertThat(validity.getValue()).isEqualTo(Duration.ofSeconds(60));
        assertThat(scheduler.tasks()).singleElement().satisfies(task ->
                assertThat(task.executeAt()).isEqualTo(NOW.plusSeconds(30)));
    }

    @Test
    void authorizedReconnectRegeneratesPlayingUrlForTheRemainingWindow() {
        startGame();
        clock.advance(Duration.ofSeconds(10));

        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.REQUEST_SNAPSHOT, 0, null, UUID.randomUUID()));

        assertThat(privateEvents).singleElement().satisfies(event -> {
            PrivateSessionEventPayload.CurrentSnapshot snapshot =
                    (PrivateSessionEventPayload.CurrentSnapshot) event.payload();
            assertThat(snapshot.currentRound().orElseThrow().playbackUrl()).contains(
                    URI.create("https://media.example.test/audio.mp3?signature=2"));
            assertThat(snapshot.currentRound().orElseThrow().startsAt()).contains(NOW);
            assertThat(snapshot.currentRound().orElseThrow().deadline()).
                    contains(NOW.plusSeconds(30));
            assertThat(snapshot.currentRound().orElseThrow().excerptStartSeconds()).contains(0);
        });
        ArgumentCaptor<Duration> validities = ArgumentCaptor.forClass(Duration.class);
        verify(mediaStorage, times(2)).presignedGetUrl(
                eq("tracks/0/audio.mp3"), validities.capture());
        assertThat(validities.getAllValues()).containsExactly(
                Duration.ofSeconds(60), Duration.ofSeconds(50));
    }

    @Test
    void nonParticipantCannotTriggerPlaybackUrlIssuance() {
        boolean handled = service.handle(SESSION_ID, UUID.randomUUID(), command(
                SessionCommandType.START_GAME, 0, null, UUID.randomUUID()));

        assertThat(handled).isFalse();
        verifyNoInteractions(mediaStorage);
        assertThat(current().version()).isZero();
    }

    @Test
    void normalAnswerTypingAfterCooldownRemainsUnaffected() {
        startGame();

        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 2, "wrong one", UUID.randomUUID()));
        clock.advance(GameplayCommandRateLimiter.PLAYER_ANSWER_COOLDOWN);
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 3, "wrong two", UUID.randomUUID()));

        assertThat(current().version()).isEqualTo(4);
        assertThat(privateEvents).extracting(event ->
                ((PrivateSessionEventPayload.AnswerRejected) event.payload()).reason()).
                containsExactly(
                        AnswerRejectionReason.WRONG_ANSWER,
                        AnswerRejectionReason.WRONG_ANSWER);
        verify(rateLimitMetrics, never()).recordPlayerCooldownRejected();
    }

    @Test
    void rapidAnswersReceiveOneStableCooldownAndThenProduceNoMoreEvents() {
        startGame();
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 2, "wrong one", UUID.randomUUID()));
        clock.advance(Duration.ofMillis(100));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 3, "wrong two", UUID.randomUUID()));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 3, "wrong three", UUID.randomUUID()));

        assertThat(current().version()).isEqualTo(3);
        assertThat(current().gameState().currentRound().orElseThrow().scoreState().totalPoints()).
                isZero();
        assertThat(privateEvents).hasSize(2);
        PrivateSessionEventPayload.AnswerRejected cooldown =
                (PrivateSessionEventPayload.AnswerRejected) privateEvents.get(1).payload();
        assertThat(cooldown.reason()).isEqualTo(AnswerRejectionReason.COOLDOWN);
        assertThat(cooldown.retryAt()).contains(NOW.plusMillis(300));
        verify(rateLimitMetrics, times(2)).recordPlayerCooldownRejected();
    }

    @Test
    void sessionCommandSpamHasBoundedFeedbackAndSkipsExpensiveSnapshotSigning() {
        for (int index = 0; index < 100; index++) {
            service.handle(SESSION_ID, ACCOUNT_ID, command(
                    SessionCommandType.REQUEST_SNAPSHOT, 0, null, UUID.randomUUID()));
        }

        assertThat(privateEvents).hasSize(21);
        assertThat(privateEvents).filteredOn(event ->
                event.type() == PrivateSessionEventType.CURRENT_SNAPSHOT).hasSize(20);
        assertThat(privateEvents).filteredOn(event ->
                event.type() == PrivateSessionEventType.ERROR).singleElement().satisfies(event -> {
                    PrivateSessionEventPayload.Error error =
                            (PrivateSessionEventPayload.Error) event.payload();
                    assertThat(error.code()).isEqualTo("SESSION_RATE_LIMITED");
                });
        verify(rateLimitMetrics, times(80)).recordSessionCommandRejected();
        verifyNoInteractions(mediaStorage);
        assertThat(current().version()).isZero();
    }

    @Test
    void titleAtDeadlineAndDeadlineTimerProduceOneTimeoutOutcome() throws Exception {
        startGame();
        ScheduledTask deadline = scheduler.tasks().getFirst();
        clock.set(deadline.executeAt());
        SessionCommand title = command(
                SessionCommandType.SUBMIT_ANSWER, 2, "Title 0", UUID.randomUUID());

        runConcurrently(deadline.task(), () -> service.handle(
                SESSION_ID, ACCOUNT_ID, title));

        assertSingleReveal(RoundOutcome.TIMEOUT);
        assertThat(current().version()).isEqualTo(3);
        assertThat(current().processedCommands().contains(title.commandId())
                || privateEvents.stream().anyMatch(event -> event.commandId().
                        filter(title.commandId()::equals).isPresent())).isTrue();
    }

    @Test
    void skipAtDeadlineAndDeadlineTimerProduceOneTimeoutOutcome() throws Exception {
        startGame();
        ScheduledTask deadline = scheduler.tasks().getFirst();
        clock.set(deadline.executeAt());
        SessionCommand skip = command(
                SessionCommandType.SKIP_ROUND, 2, null, UUID.randomUUID());

        runConcurrently(deadline.task(), () -> service.handle(
                SESSION_ID, ACCOUNT_ID, skip));

        assertSingleReveal(RoundOutcome.TIMEOUT);
        assertThat(current().version()).isEqualTo(3);
    }

    @Test
    void duplicateDeadlineAndAdvanceTimersEachTransitionExactlyOnce() {
        startGame();
        ScheduledTask deadline = scheduler.tasks().getFirst();
        clock.set(deadline.executeAt());

        deadline.task().run();
        deadline.task().run();

        assertSingleReveal(RoundOutcome.TIMEOUT);
        assertThat(current().version()).isEqualTo(3);
        ScheduledTask advance = scheduler.tasks().get(1);
        clock.set(advance.executeAt());
        advance.task().run();
        advance.task().run();

        ActiveSessionSnapshot progressed = current();
        assertThat(progressed.version()).isEqualTo(5);
        assertThat(progressed.gameState().currentRoundIndex()).isEqualTo(1);
        assertThat(progressed.gameState().currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PLAYING);
        assertThat(publicEvents).filteredOn(event ->
                event.type() == PublicSessionEventType.ROUND_PREPARING).hasSize(1);
    }

    @Test
    void manualAdvanceAndAutomaticAdvanceRaceProduceOneNextRound() throws Exception {
        startGame();
        SessionCommand skip = command(
                SessionCommandType.SKIP_ROUND, 2, null, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(1));
        service.handle(SESSION_ID, ACCOUNT_ID, skip);
        ScheduledTask advance = scheduler.tasks().get(1);
        clock.set(advance.executeAt());
        SessionCommand manual = command(
                SessionCommandType.ADVANCE_ROUND, 3, null, UUID.randomUUID());

        runConcurrently(advance.task(), () -> service.handle(
                SESSION_ID, ACCOUNT_ID, manual));

        ActiveSessionSnapshot progressed = current();
        assertThat(progressed.version()).isEqualTo(5);
        assertThat(progressed.gameState().currentRoundIndex()).isEqualTo(1);
        assertThat(progressed.gameState().completedRounds()).hasSize(1);
        assertThat(publicEvents).filteredOn(event ->
                event.type() == PublicSessionEventType.ROUND_PREPARING).hasSize(1);
    }

    @Test
    void reconnectSnapshotKeepsRevealDeadlineAndCannotCreateASecondTransition()
            throws Exception {
        startGame();
        SessionCommand skip = command(
                SessionCommandType.SKIP_ROUND, 2, null, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(2));
        service.handle(SESSION_ID, ACCOUNT_ID, skip);
        ScheduledTask advance = scheduler.tasks().get(1);
        Instant expectedAdvanceAt = clock.instant().plus(SessionGameplayService.REVEAL_DURATION);
        SessionCommand snapshot = command(
                SessionCommandType.REQUEST_SNAPSHOT, 0, null, UUID.randomUUID());

        service.handle(SESSION_ID, ACCOUNT_ID, snapshot);

        PrivateSessionEvent recovery = privateEvents.stream().
                filter(event -> event.type() == PrivateSessionEventType.CURRENT_SNAPSHOT).
                findFirst().orElseThrow();
        PrivateSessionEventPayload.CurrentSnapshot payload =
                (PrivateSessionEventPayload.CurrentSnapshot) recovery.payload();
        assertThat(payload.currentRound().orElseThrow().advanceAt()).contains(expectedAdvanceAt);
        assertThat(payload.currentRound().orElseThrow().status()).isEqualTo(RoundStatus.REVEALED);

        clock.set(advance.executeAt());
        runConcurrently(advance.task(), () -> service.handle(
                SESSION_ID, ACCOUNT_ID, snapshot));

        assertThat(current().version()).isEqualTo(5);
        assertThat(current().gameState().completedRounds()).hasSize(1);
    }

    @Test
    void duplicateCommandIdCannotApplyASecondStateChange() {
        startGame();
        UUID commandId = UUID.randomUUID();
        SessionCommand skip = command(SessionCommandType.SKIP_ROUND, 2, null, commandId);
        clock.advance(Duration.ofSeconds(1));

        service.handle(SESSION_ID, ACCOUNT_ID, skip);
        service.handle(SESSION_ID, ACCOUNT_ID, skip);

        assertThat(current().version()).isEqualTo(3);
        assertThat(current().gameState().currentRound().orElseThrow().outcome()).
                contains(RoundOutcome.SKIPPED);
        assertThat(privateEvents).singleElement().satisfies(event -> {
            assertThat(event.commandId()).contains(commandId);
            assertThat(((PrivateSessionEventPayload.Error) event.payload()).code()).
                    isEqualTo("DUPLICATE_COMMAND");
        });
    }

    @Test
    void finalRevealTimerFinishesGameAndPublishesSummaryExactlyOnce() {
        startGame();
        for (int roundIndex = 0; roundIndex < 5; roundIndex++) {
            ActiveSessionSnapshot playing = current();
            clock.advance(Duration.ofSeconds(1));
            service.handle(
                    SESSION_ID,
                    ACCOUNT_ID,
                    command(
                            SessionCommandType.SKIP_ROUND,
                            playing.version(),
                            null,
                            UUID.randomUUID()));
            ScheduledTask advance = scheduler.tasks().getLast();
            clock.set(advance.executeAt());
            advance.task().run();
            advance.task().run();
        }

        ActiveSessionSnapshot finished = current();
        assertThat(finished.gameState().status()).isEqualTo(GameStatus.FINISHED);
        assertThat(finished.gameState().completedRounds()).hasSize(5);
        assertThat(finished.expiresAt()).
                isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
        assertThat(scheduler.cancelledSessions()).containsExactly(new SessionId(SESSION_ID));
        assertThat(publicEvents).filteredOn(event ->
                event.type() == PublicSessionEventType.GAME_FINISHED).singleElement().
                satisfies(event -> {
                    PublicSessionEventPayload.GameFinished payload =
                            (PublicSessionEventPayload.GameFinished) event.payload();
                    assertThat(payload.skippedRounds()).isEqualTo(5);
                    assertThat(payload.totalScore()).isZero();
                });
        verify(historyCompletionService).complete(finished);
    }

    @Test
    void pausedSoloRoundHoldsItsDeadlineAndResumesWithTheSameRemainingTime() {
        startGame();
        ScheduledTask originalDeadline = scheduler.tasks().getFirst();
        clock.advance(Duration.ofSeconds(10));

        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.PAUSE_ROUND, 2, null, UUID.randomUUID()));

        assertThat(current().version()).isEqualTo(3);
        assertThat(publicEvents).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(PublicSessionEventType.ROUND_PAUSED);
            PublicSessionEventPayload.RoundPaused paused =
                    (PublicSessionEventPayload.RoundPaused) event.payload();
            assertThat(paused.status()).isEqualTo(RoundStatus.PLAYING);
            assertThat(paused.pausedAt()).isEqualTo(NOW.plusSeconds(10));
        });
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 3, "Title 0", UUID.randomUUID()));
        assertThat(privateEvents).singleElement().satisfies(event ->
                assertThat(((PrivateSessionEventPayload.AnswerRejected) event.payload()).reason()).
                        isEqualTo(AnswerRejectionReason.INVALID_ROUND_STATE));
        clock.set(originalDeadline.executeAt());
        originalDeadline.task().run();
        assertThat(current().gameState().currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PLAYING);

        clock.set(NOW.plusSeconds(110));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.RESUME_ROUND, 3, null, UUID.randomUUID()));

        PublicSessionEventPayload.RoundResumed resumed =
                (PublicSessionEventPayload.RoundResumed) publicEvents.getLast().payload();
        assertThat(resumed.status()).isEqualTo(RoundStatus.PLAYING);
        assertThat(resumed.startsAt()).contains(NOW.plusSeconds(100));
        assertThat(resumed.deadline()).contains(NOW.plusSeconds(130));
        assertThat(resumed.playbackUrl()).contains(
                URI.create("https://media.example.test/audio.mp3?signature=2"));
        assertThat(scheduler.tasks().getLast().executeAt()).isEqualTo(NOW.plusSeconds(130));

        clock.advance(Duration.ofSeconds(5));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SUBMIT_ANSWER, 4, "Title 0", UUID.randomUUID()));
        // 15 of the 30 seconds remain: 10 s before the pause and 5 s after it.
        assertThat(current().gameState().currentRound().orElseThrow().scoreState().totalPoints()).
                isEqualTo(500);
    }

    @Test
    void pausedRevealHoldsTheNextRoundUntilItResumes() {
        startGame();
        clock.advance(Duration.ofSeconds(1));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SKIP_ROUND, 2, null, UUID.randomUUID()));
        ScheduledTask originalAdvance = scheduler.tasks().getLast();
        clock.advance(Duration.ofSeconds(1));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.PAUSE_ROUND, 3, null, UUID.randomUUID()));

        clock.set(originalAdvance.executeAt());
        originalAdvance.task().run();
        assertThat(current().gameState().currentRoundIndex()).isZero();
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.REQUEST_SNAPSHOT, 0, null, UUID.randomUUID()));
        PrivateSessionEventPayload.CurrentRound recovered =
                ((PrivateSessionEventPayload.CurrentSnapshot) privateEvents.getLast().payload()).
                        currentRound().orElseThrow();
        assertThat(recovered.pausedAt()).contains(NOW.plusSeconds(2));
        assertThat(recovered.advanceAt()).contains(NOW.plusSeconds(5));

        clock.set(NOW.plusSeconds(12));
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.RESUME_ROUND, 4, null, UUID.randomUUID()));

        PublicSessionEventPayload.RoundResumed resumed =
                (PublicSessionEventPayload.RoundResumed) publicEvents.getLast().payload();
        assertThat(resumed.status()).isEqualTo(RoundStatus.REVEALED);
        assertThat(resumed.transitionAt()).contains(NOW.plusSeconds(15));
        ScheduledTask delayedAdvance = scheduler.tasks().getLast();
        assertThat(delayedAdvance.executeAt()).isEqualTo(NOW.plusSeconds(15));
        clock.set(delayedAdvance.executeAt());
        delayedAdvance.task().run();
        assertThat(current().gameState().currentRoundIndex()).isEqualTo(1);
    }

    @Test
    void multiplayerHostCanPauseOnlyTheRevealAndGuestsCannotPause() {
        assertThat(store.create(multiplayerSession(), clock.instant())).
                isEqualTo(SessionCreateOutcome.CREATED);
        service.handle(MULTIPLAYER_SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.START_GAME, 0, null, UUID.randomUUID()));
        clock.advance(Duration.ofSeconds(2));

        service.handle(MULTIPLAYER_SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.PAUSE_ROUND, multiplayerVersion(), null, UUID.randomUUID()));
        service.handle(MULTIPLAYER_SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.SKIP_ROUND, multiplayerVersion(), null, UUID.randomUUID()));
        service.handle(MULTIPLAYER_SESSION_ID, GUEST_ACCOUNT_ID, command(
                SessionCommandType.SKIP_ROUND, multiplayerVersion(), null, UUID.randomUUID()));
        service.handle(MULTIPLAYER_SESSION_ID, GUEST_ACCOUNT_ID, command(
                SessionCommandType.PAUSE_ROUND, multiplayerVersion(), null, UUID.randomUUID()));
        service.handle(MULTIPLAYER_SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.PAUSE_ROUND, multiplayerVersion(), null, UUID.randomUUID()));

        assertThat(privateEvents).filteredOn(event ->
                event.type() == PrivateSessionEventType.ERROR).
                extracting(event -> ((PrivateSessionEventPayload.Error) event.payload()).code()).
                containsExactly("ROUND_PAUSE_UNAVAILABLE", "SESSION_OWNER_REQUIRED");
        assertThat(store.find(new SessionId(MULTIPLAYER_SESSION_ID), clock.instant()).
                orElseThrow().gameState().currentRound().orElseThrow().pausedAt()).
                contains(clock.instant());
    }

    private long multiplayerVersion() {
        return store.find(new SessionId(MULTIPLAYER_SESSION_ID), clock.instant()).
                orElseThrow().version();
    }

    private ActiveSessionSnapshot multiplayerSession() {
        ActiveSessionSnapshot solo = createdSession();
        GameParticipantId host = new GameParticipantId(PARTICIPANT_ID);
        GameParticipantId guest = new GameParticipantId(GUEST_PARTICIPANT_ID);
        GameStateSnapshot game = GameStateSnapshot.created(
                solo.configuration(), new GameTrackSelection(tracks()), List.of(host, guest));
        return new ActiveSessionSnapshot(
                new SessionId(MULTIPLAYER_SESSION_ID),
                host,
                List.of(
                        new SessionParticipant(host, Optional.of(ACCOUNT_ID), "Host"),
                        new SessionParticipant(guest, Optional.of(GUEST_ACCOUNT_ID), "Guest")),
                solo.configuration(),
                game,
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                0,
                NOW,
                NOW,
                NOW.plus(Duration.ofHours(1)), 0);
    }

    private void startGame() {
        service.handle(SESSION_ID, ACCOUNT_ID, command(
                SessionCommandType.START_GAME, 0, null, UUID.randomUUID()));
        publicEvents.clear();
    }

    private void assertSingleReveal(RoundOutcome outcome) {
        ActiveSessionSnapshot state = current();
        assertThat(state.gameState().currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.REVEALED);
        assertThat(state.gameState().currentRound().orElseThrow().outcome()).contains(outcome);
        assertThat(publicEvents).filteredOn(event ->
                event.type() == PublicSessionEventType.ROUND_REVEALED).singleElement();
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> firstResult = executor.submit(() -> awaitAndRun(ready, start, first));
            Future<?> secondResult = executor.submit(() -> awaitAndRun(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstResult.get(5, TimeUnit.SECONDS);
            secondResult.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitAndRun(CountDownLatch ready, CountDownLatch start, Runnable task) {
        try {
            ready.countDown();
            start.await();
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private SessionCommand command(
            SessionCommandType type,
            long version,
            String answer,
            UUID commandId) {
        return new SessionCommand(SessionProtocol.VERSION, commandId, version, type, answer);
    }

    private void assertPersisted(SessionEventMetadata metadata) {
        assertThat(store.find(new SessionId(metadata.sessionId()), clock.instant()).
                orElseThrow().version()).isGreaterThanOrEqualTo(metadata.sessionVersion());
    }

    private ActiveSessionSnapshot current() {
        return store.find(new SessionId(SESSION_ID), clock.instant()).orElseThrow();
    }

    private ActiveSessionSnapshot createdSession() {
        UUID playlistId = UUID.randomUUID();
        GameConfiguration configuration = new GameConfigurationValidator().validate(
                new RequestedGameConfiguration(playlistId, 5, 30, Map.of()),
                new PlaylistEligibilitySnapshot(playlistId, 5));
        GameParticipantId participantId = new GameParticipantId(PARTICIPANT_ID);
        GameStateSnapshot game = GameStateSnapshot.created(
                configuration, new GameTrackSelection(tracks()), List.of(participantId));
        return new ActiveSessionSnapshot(
                new SessionId(SESSION_ID),
                participantId,
                List.of(new SessionParticipant(
                        participantId, Optional.of(ACCOUNT_ID), "Owner")),
                configuration,
                game,
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                0,
                NOW,
                NOW,
                NOW.plus(Duration.ofHours(1)), 0);
    }

    private List<GameTrackSnapshot> tracks() {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            tracks.add(new GameTrackSnapshot(
                    UUID.randomUUID(),
                    index,
                    new GameAnswerSnapshot(
                            "Title " + index,
                            "Artist " + index,
                            "Composer " + index,
                            "Album " + index,
                            Map.of()),
                    new GamePlaybackSnapshot(index, 180),
                    new GameMediaSnapshot(
                            "tracks/" + index + "/audio.mp3", "checksum-" + index)));
        }
        return tracks;
    }

    private record ScheduledTask(Instant executeAt, Runnable task) {
    }

    private static final class RecordingScheduler implements SessionTaskScheduler {

        private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
        private final List<SessionId> cancelledSessions = new CopyOnWriteArrayList<>();

        @Override
        public void schedule(Instant executeAt, Runnable task) {
            tasks.add(new ScheduledTask(executeAt, task));
        }

        @Override
        public int cancelSessionTasks(SessionId sessionId) {
            cancelledSessions.add(sessionId);
            return 0;
        }

        List<ScheduledTask> tasks() {
            return tasks;
        }

        List<SessionId> cancelledSessions() {
            return cancelledSessions;
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
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
}

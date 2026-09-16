package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.InMemorySessionStore;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T17:00:00Z");

    private final SessionStore store = new InMemorySessionStore();
    private final SessionTaskScheduler scheduler = mock(SessionTaskScheduler.class);
    private final GameplayCommandRateLimiter rateLimiter = mock(GameplayCommandRateLimiter.class);
    private final SessionCleanupMetrics metrics = mock(SessionCleanupMetrics.class);
    private final SessionCleanupService service = new SessionCleanupService(
            store, scheduler, rateLimiter, metrics, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void expiresAbandonedGameAndReleasesTimersAndRateState() {
        ActiveSessionSnapshot expired = expiredCreatedSession();
        assertThat(store.create(expired, expired.createdAt())).
                isEqualTo(SessionCreateOutcome.CREATED);
        when(scheduler.cancelSessionTasks(expired.sessionId())).thenReturn(2);

        service.cleanup();

        assertThat(store.find(expired.sessionId(), NOW)).isEmpty();
        verify(scheduler).cancelSessionTasks(expired.sessionId());
        verify(rateLimiter).forget(expired.sessionId());
        verify(metrics).recordAbandoned();
        verify(metrics).recordCancelledTimers(2);
        verify(metrics).recordSuccess(0);
    }

    @Test
    void retainsUnexpiredGameAndItsVolatileResources() {
        ActiveSessionSnapshot active = activeCreatedSession();
        assertThat(store.create(active, active.createdAt())).
                isEqualTo(SessionCreateOutcome.CREATED);

        service.cleanup();

        assertThat(store.find(active.sessionId(), NOW)).contains(active);
        verifyNoInteractions(scheduler, rateLimiter);
        verify(metrics).recordSuccess(1);
    }

    @Test
    void removesOnlyFinishedActiveCopyWithoutTouchingDurableHistoryServices() {
        ActiveSessionSnapshot source = FinishedSessionFixture.finished();
        ActiveSessionSnapshot expired = new ActiveSessionSnapshot(
                source.sessionId(), source.ownerParticipantId(), source.participants(),
                source.configuration(), source.gameState(), source.discoveries(),
                source.processedCommands(), source.version(), source.createdAt(),
                source.updatedAt(), NOW);
        assertThat(store.create(expired, source.createdAt())).
                isEqualTo(SessionCreateOutcome.CREATED);

        service.cleanup();

        assertThat(store.find(expired.sessionId(), NOW)).isEmpty();
        verify(metrics).recordTerminal();
        verify(scheduler).cancelSessionTasks(expired.sessionId());
        verify(rateLimiter).forget(expired.sessionId());
    }

    @Test
    void reportsCleanupFailureWithoutThrowingFromScheduledBoundary() {
        SessionStore failingStore = mock(SessionStore.class);
        when(failingStore.removeExpiredSessions(NOW)).
                thenThrow(new IllegalStateException("store unavailable"));
        SessionCleanupService failingService = new SessionCleanupService(
                failingStore, scheduler, rateLimiter, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC));

        failingService.cleanup();

        verify(metrics).recordFailure();
        verifyNoInteractions(scheduler, rateLimiter);
    }

    private ActiveSessionSnapshot expiredCreatedSession() {
        return createdSession(NOW);
    }

    private ActiveSessionSnapshot activeCreatedSession() {
        return createdSession(NOW.plus(Duration.ofMinutes(1)));
    }

    private ActiveSessionSnapshot createdSession(Instant expiresAt) {
        ActiveSessionSnapshot source = FinishedSessionFixture.finished();
        Instant createdAt = NOW.minus(Duration.ofMinutes(30));
        GameStateSnapshot created = GameStateSnapshot.created(
                source.configuration(), source.gameState().trackSelection());
        return new ActiveSessionSnapshot(
                source.sessionId(), source.ownerParticipantId(), source.participants(),
                source.configuration(), created, SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(), 0, createdAt, createdAt, expiresAt);
    }
}

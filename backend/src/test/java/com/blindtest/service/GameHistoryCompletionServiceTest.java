package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.SessionRemoveOutcome;
import com.blindtest.store.SessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

class GameHistoryCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T16:00:00Z");

    private final GameHistoryPersistenceService persistenceService =
            mock(GameHistoryPersistenceService.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final SessionTaskScheduler scheduler = mock(SessionTaskScheduler.class);
    private final GameHistoryCompletionMetrics metrics = mock(GameHistoryCompletionMetrics.class);
    private final GameHistoryCompletionService service = new GameHistoryCompletionService(
            persistenceService,
            sessionStore,
            scheduler,
            metrics,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final ActiveSessionSnapshot session = FinishedSessionFixture.finished();

    @BeforeEach
    void defaultRemoval() {
        when(sessionStore.remove(any(), eq(session.version()), eq(NOW))).
                thenReturn(SessionRemoveOutcome.REMOVED);
    }

    @Test
    void durableSuccessRemovesExactFinishedActiveVersion() {
        when(persistenceService.persist(session)).
                thenReturn(GameHistoryPersistenceOutcome.CREATED);

        service.complete(session);

        verify(metrics).recordCreated();
        verify(sessionStore).remove(session.sessionId(), session.version(), NOW);
        verify(scheduler, never()).schedule(any(), any());
    }

    @Test
    void transientFailureRetainsStateAndRetriesOnceAtScheduledBoundary() {
        when(persistenceService.persist(session)).
                thenThrow(new TransientDataAccessResourceException("database unavailable")).
                thenReturn(GameHistoryPersistenceOutcome.CREATED);
        ArgumentCaptor<Instant> executeAt = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        service.complete(session);

        verify(sessionStore, never()).remove(any(), any(Long.class), any());
        verify(scheduler).schedule(executeAt.capture(), task.capture());
        assertThat(executeAt.getValue()).isEqualTo(NOW.plusSeconds(5));
        verify(metrics).recordRetryScheduled();

        task.getValue().run();

        verify(persistenceService, times(2)).persist(session);
        verify(sessionStore).remove(session.sessionId(), session.version(), NOW);
    }

    @Test
    void duplicateTransientCompletionSchedulesOnlyOneRetry() {
        when(persistenceService.persist(session)).
                thenThrow(new TransientDataAccessResourceException("database unavailable"));

        service.complete(session);
        service.complete(session);

        verify(scheduler).schedule(any(), any());
        verify(sessionStore, never()).remove(any(), any(Long.class), any());
    }

    @Test
    void concurrentUniqueInsertIsConfirmedBeforeActiveStateRemoval() {
        when(persistenceService.persist(session)).
                thenThrow(new DataIntegrityViolationException("unique session"));
        when(persistenceService.exists(FinishedSessionFixture.SESSION_ID)).thenReturn(true);

        service.complete(session);

        verify(metrics).recordAlreadyExists();
        verify(sessionStore).remove(session.sessionId(), session.version(), NOW);
    }

    @Test
    void permanentFailureIsVisibleAndKeepsRecoverableActiveState() {
        when(persistenceService.persist(session)).thenThrow(new IllegalStateException("broken"));

        service.complete(session);

        verify(metrics).recordFailed();
        verify(sessionStore, never()).remove(any(), any(Long.class), any());
        verify(scheduler, never()).schedule(any(), any());
    }
}

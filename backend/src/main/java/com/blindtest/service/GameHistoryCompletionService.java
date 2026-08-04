package com.blindtest.service;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.SessionStore;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

/** Coordinates durable completion, deduplicated transient retries, and active-state removal. */
@Service
public class GameHistoryCompletionService {

    static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    static final Duration MAXIMUM_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Logger LOG = LoggerFactory.getLogger(GameHistoryCompletionService.class);

    private final GameHistoryPersistenceService persistenceService;
    private final SessionStore sessionStore;
    private final SessionTaskScheduler scheduler;
    private final GameHistoryCompletionMetrics metrics;
    private final Clock clock;
    private final ConcurrentMap<SessionId, Integer> pendingRetries = new ConcurrentHashMap<>();

    /** Wires transaction, live state, scheduling, observability, and authoritative time. */
    public GameHistoryCompletionService(
            GameHistoryPersistenceService persistenceService,
            SessionStore sessionStore,
            SessionTaskScheduler scheduler,
            GameHistoryCompletionMetrics metrics,
            Clock clock) {
        this.persistenceService = persistenceService;
        this.sessionStore = sessionStore;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Attempts persistence immediately and retains the immutable result until success. */
    public void complete(ActiveSessionSnapshot session) {
        attempt(session, 0);
    }

    private void attempt(ActiveSessionSnapshot session, int attempt) {
        try {
            GameHistoryPersistenceOutcome outcome = persistenceService.persist(session);
            record(outcome);
            pendingRetries.remove(session.sessionId());
            sessionStore.remove(session.sessionId(), session.version(), clock.instant());
        } catch (DataIntegrityViolationException exception) {
            confirmConcurrentInsertOrFail(session, exception);
        } catch (TransientDataAccessException | CannotCreateTransactionException exception) {
            scheduleRetry(session, attempt + 1);
        } catch (RuntimeException exception) {
            metrics.recordFailed();
            LOG.error("Completed game history persistence failed; active state was retained", exception);
        }
    }

    private void confirmConcurrentInsertOrFail(
            ActiveSessionSnapshot session,
            DataIntegrityViolationException exception) {
        boolean confirmed = false;
        try {
            if (persistenceService.exists(session.sessionId().value())) {
                metrics.recordAlreadyExists();
                pendingRetries.remove(session.sessionId());
                sessionStore.remove(session.sessionId(), session.version(), clock.instant());
                confirmed = true;
            }
        } catch (TransientDataAccessException | CannotCreateTransactionException transientFailure) {
            scheduleRetry(session, 1);
            confirmed = true;
        }
        if (!confirmed) {
            metrics.recordFailed();
            LOG.error("Completed game history constraint failure; active state was retained", exception);
        }
    }

    private void scheduleRetry(ActiveSessionSnapshot session, int attempt) {
        Integer existing = pendingRetries.putIfAbsent(session.sessionId(), attempt);
        if (existing != null) {
            return;
        }
        schedulePending(session, attempt);
    }

    private void schedulePending(ActiveSessionSnapshot session, int attempt) {
        Duration delay = retryDelay(attempt);
        try {
            scheduler.schedule(clock.instant().plus(delay), () -> retry(session, attempt));
            metrics.recordRetryScheduled();
            LOG.warn("Transient game history persistence failure; a safe retry was scheduled");
        } catch (RuntimeException exception) {
            pendingRetries.remove(session.sessionId(), attempt);
            metrics.recordFailed();
            LOG.error("Game history retry scheduling failed; active state was retained", exception);
        }
    }

    private void retry(ActiveSessionSnapshot session, int attempt) {
        if (!pendingRetries.remove(session.sessionId(), attempt)) {
            return;
        }
        attempt(session, attempt);
    }

    private static Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
        long seconds = Math.min(
                INITIAL_RETRY_DELAY.toSeconds() * multiplier,
                MAXIMUM_RETRY_DELAY.toSeconds());
        return Duration.ofSeconds(seconds);
    }

    private void record(GameHistoryPersistenceOutcome outcome) {
        if (outcome == GameHistoryPersistenceOutcome.CREATED) {
            metrics.recordCreated();
        } else {
            metrics.recordAlreadyExists();
        }
    }
}

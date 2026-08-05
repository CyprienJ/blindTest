package com.blindtest.service;

import com.blindtest.model.game.GameStateMachine;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RoundStateMachine;
import com.blindtest.model.game.TitleScoreCalculator;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.SessionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Periodically expires abandoned process-local sessions and their volatile resources. */
@Service
public class SessionCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupService.class);

    private final SessionStore sessionStore;
    private final SessionTaskScheduler scheduler;
    private final GameplayCommandRateLimiter rateLimiter;
    private final SessionCleanupMetrics metrics;
    private final Clock clock;
    private final GameStateMachine gameStateMachine = new GameStateMachine(
            new RoundStateMachine(new TitleScoreCalculator()));

    /** Wires active state, volatile resources, observability, and authoritative time. */
    public SessionCleanupService(
            SessionStore sessionStore,
            SessionTaskScheduler scheduler,
            GameplayCommandRateLimiter rateLimiter,
            SessionCleanupMetrics metrics,
            Clock clock) {
        this.sessionStore = sessionStore;
        this.scheduler = scheduler;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Runs one bounded cleanup pass; failures are observable and do not stop scheduling. */
    @Scheduled(fixedDelayString = "${app.session.cleanup-delay:PT1M}")
    public void cleanup() {
        Instant now = clock.instant();
        try {
            List<ActiveSessionSnapshot> expired = sessionStore.removeExpiredSessions(now);
            expired.forEach(this::release);
            metrics.recordSuccess(sessionStore.activeCount(now));
        } catch (RuntimeException exception) {
            metrics.recordFailure();
            LOG.error("Active-session cleanup failed", exception);
        }
    }

    private void release(ActiveSessionSnapshot session) {
        GameStatus status = session.gameState().status();
        if (status == GameStatus.CREATED || status == GameStatus.PLAYING) {
            // Apply the domain terminal transition before releasing the abandoned aggregate.
            gameStateMachine.abort(session.gameState());
            metrics.recordAbandoned();
        } else {
            metrics.recordTerminal();
        }
        int cancelled = scheduler.cancelSessionTasks(session.sessionId());
        metrics.recordCancelledTimers(cancelled);
        rateLimiter.forget(session.sessionId());
    }
}

package com.blindtest.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

    private final SessionStore store = new InMemorySessionStore();

    @Test
    void createsReadsAndRejectsAnActiveIdentityCollision() {
        ActiveSessionSnapshot session = SessionTestFixtures.session();

        assertThat(store.create(session, SessionTestFixtures.NOW)).
                isEqualTo(SessionCreateOutcome.CREATED);
        assertThat(store.create(session, SessionTestFixtures.NOW)).
                isEqualTo(SessionCreateOutcome.ALREADY_EXISTS);
        assertThat(store.find(session.sessionId(), SessionTestFixtures.NOW)).contains(session);
        assertThat(store.activeCount(SessionTestFixtures.NOW)).isOne();
    }

    @Test
    void compareAndSetRejectsAStaleVersionWithoutOverwritingTheWinner() {
        ActiveSessionSnapshot initial = SessionTestFixtures.session();
        store.create(initial, SessionTestFixtures.NOW);
        ActiveSessionSnapshot winner = next(initial, 1);
        ActiveSessionSnapshot stale = next(initial, 2);

        assertThat(store.compareAndSet(
                        initial.sessionId(), 0, winner, SessionTestFixtures.NOW.plusSeconds(1))).
                isEqualTo(SessionUpdateOutcome.UPDATED);
        assertThat(store.compareAndSet(
                        initial.sessionId(), 0, stale, SessionTestFixtures.NOW.plusSeconds(2))).
                isEqualTo(SessionUpdateOutcome.VERSION_CONFLICT);
        assertThat(store.find(initial.sessionId(), SessionTestFixtures.NOW.plusSeconds(2))).
                contains(winner);
    }

    @Test
    void concurrentCommandsProduceExactlyOneVersionWinner() throws Exception {
        ActiveSessionSnapshot initial = SessionTestFixtures.session();
        store.create(initial, SessionTestFixtures.NOW);
        int commandCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(commandCount);
        CountDownLatch ready = new CountDownLatch(commandCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SessionUpdateOutcome>> futures = new ArrayList<>();
        try {
            for (int command = 0; command < commandCount; command++) {
                int commandIndex = command;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.compareAndSet(
                            initial.sessionId(),
                            0,
                            next(initial, commandIndex + 1),
                            SessionTestFixtures.NOW.plusSeconds(1));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<SessionUpdateOutcome> outcomes = new ArrayList<>();
            for (Future<SessionUpdateOutcome> future : futures) {
                outcomes.add(future.get(5, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(SessionUpdateOutcome.UPDATED::equals).hasSize(1);
            assertThat(outcomes).filteredOn(SessionUpdateOutcome.VERSION_CONFLICT::equals).
                    hasSize(commandCount - 1);
            assertThat(store.find(initial.sessionId(), SessionTestFixtures.NOW.plusSeconds(1)).
                    orElseThrow().version()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void readAndCreateLazilyReplaceAnExpiredSession() {
        ActiveSessionSnapshot expired = SessionTestFixtures.session();
        store.create(expired, SessionTestFixtures.NOW);
        Instant expiry = expired.expiresAt();

        assertThat(store.find(expired.sessionId(), expiry)).isEmpty();
        ActiveSessionSnapshot replacement = SessionTestFixtures.recreatedAt(expired, expiry);
        assertThat(store.create(replacement, expiry)).isEqualTo(SessionCreateOutcome.CREATED);
        assertThat(store.find(replacement.sessionId(), expiry)).contains(replacement);
    }

    @Test
    void versionGuardedRemovalPreservesNewerState() {
        ActiveSessionSnapshot initial = SessionTestFixtures.session();
        store.create(initial, SessionTestFixtures.NOW);
        ActiveSessionSnapshot updated = next(initial, 1);
        store.compareAndSet(
                initial.sessionId(), 0, updated, SessionTestFixtures.NOW.plusSeconds(1));

        assertThat(store.remove(
                        initial.sessionId(), 0, SessionTestFixtures.NOW.plusSeconds(2))).
                isEqualTo(SessionRemoveOutcome.VERSION_CONFLICT);
        assertThat(store.remove(
                        initial.sessionId(), 1, SessionTestFixtures.NOW.plusSeconds(2))).
                isEqualTo(SessionRemoveOutcome.REMOVED);
        assertThat(store.remove(
                        initial.sessionId(), 1, SessionTestFixtures.NOW.plusSeconds(2))).
                isEqualTo(SessionRemoveOutcome.NOT_FOUND);
    }

    @Test
    void bulkExpiryRemovesOnlySessionsAtOrPastTheirDeadline() {
        ActiveSessionSnapshot first = SessionTestFixtures.session();
        ActiveSessionSnapshot second = SessionTestFixtures.sessionWithSuffix(2);
        store.create(first, SessionTestFixtures.NOW);
        store.create(second, SessionTestFixtures.NOW);
        ActiveSessionSnapshot extended = second.next(
                second.gameState(),
                second.discoveries(),
                SessionTestFixtures.NOW.plusSeconds(1),
                first.expiresAt().plusSeconds(60));
        store.compareAndSet(
                second.sessionId(), 0, extended, SessionTestFixtures.NOW.plusSeconds(1));

        assertThat(store.removeExpired(first.expiresAt())).isOne();
        assertThat(store.find(first.sessionId(), first.expiresAt())).isEmpty();
        assertThat(store.find(second.sessionId(), first.expiresAt())).contains(extended);
        assertThat(store.activeCount(first.expiresAt())).isOne();
    }

    @Test
    void rejectsMalformedReplacementAndAlreadyExpiredWrites() {
        ActiveSessionSnapshot initial = SessionTestFixtures.session();
        store.create(initial, SessionTestFixtures.NOW);

        assertThatThrownBy(() -> store.compareAndSet(
                        initial.sessionId(),
                        0,
                        initial,
                        SessionTestFixtures.NOW)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("replacement must increment version exactly once");
        assertThatThrownBy(() -> store.create(initial, initial.expiresAt())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("session must not already be expired");
    }

    private ActiveSessionSnapshot next(ActiveSessionSnapshot initial, int commandIndex) {
        Instant updatedAt = SessionTestFixtures.NOW.plusSeconds(1);
        return initial.next(
                initial.gameState(),
                SecondaryDiscoveryState.empty(),
                updatedAt,
                updatedAt.plus(Duration.ofMinutes(30)).plusNanos(commandIndex));
    }
}

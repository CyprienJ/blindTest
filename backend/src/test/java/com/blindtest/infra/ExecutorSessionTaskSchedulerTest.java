package com.blindtest.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.session.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ExecutorSessionTaskSchedulerTest {

    @Test
    void cancelsOnlyQueuedTasksOwnedByTheExpiredSession() {
        Instant now = Instant.parse("2026-08-04T17:00:00Z");
        ExecutorSessionTaskScheduler scheduler = new ExecutorSessionTaskScheduler(
                Clock.fixed(now, ZoneOffset.UTC));
        SessionId first = new SessionId(UUID.randomUUID());
        SessionId second = new SessionId(UUID.randomUUID());
        AtomicBoolean ran = new AtomicBoolean();
        try {
            scheduler.schedule(first, now.plus(Duration.ofHours(1)), () -> ran.set(true));
            scheduler.schedule(first, now.plus(Duration.ofHours(1)), () -> ran.set(true));
            scheduler.schedule(second, now.plus(Duration.ofHours(1)), () -> ran.set(true));

            assertThat(scheduler.cancelSessionTasks(first)).isEqualTo(2);
            assertThat(scheduler.cancelSessionTasks(first)).isZero();
            assertThat(scheduler.cancelSessionTasks(second)).isOne();
            assertThat(ran).isFalse();
        } finally {
            scheduler.shutdown();
        }
    }
}

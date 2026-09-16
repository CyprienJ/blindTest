package com.blindtest.service;

import com.blindtest.repository.MediaDeletionTaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Safe aggregate metrics for media deletion and reconciliation operations. */
@Component
public class MediaOperationalMetrics {

    private final Counter deletionSucceeded;
    private final Counter deletionFailed;
    private final AtomicLong missingObjects = new AtomicLong();
    private final AtomicLong orphanedObjects = new AtomicLong();

    /** Registers aggregate media gauges and counters. */
    public MediaOperationalMetrics(
            MeterRegistry registry,
            MediaDeletionTaskRepository deletionTaskRepository) {
        deletionSucceeded = registry.counter("blindtest.media.deletion.succeeded");
        deletionFailed = registry.counter("blindtest.media.deletion.failed");
        Gauge.builder("blindtest.media.deletion.pending", deletionTaskRepository,
                        MediaDeletionTaskRepository::count).
                register(registry);
        Gauge.builder("blindtest.media.reconciliation.missing", missingObjects, AtomicLong::get).
                register(registry);
        Gauge.builder("blindtest.media.reconciliation.orphaned", orphanedObjects, AtomicLong::get).
                register(registry);
    }

    /** Records a successful object deletion. */
    public void recordDeletionSucceeded() {
        deletionSucceeded.increment();
    }

    /** Records a failed deletion attempt. */
    public void recordDeletionFailed() {
        deletionFailed.increment();
    }

    /** Updates the latest reconciliation counts. */
    public void recordReconciliation(long missing, long orphaned) {
        missingObjects.set(missing);
        orphanedObjects.set(orphaned);
    }
}

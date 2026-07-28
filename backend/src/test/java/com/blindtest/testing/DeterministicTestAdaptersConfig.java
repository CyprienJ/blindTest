package com.blindtest.testing;

import com.blindtest.security.TokenGenerator;
import com.blindtest.service.ImportJobRunner;
import com.blindtest.service.port.ImportJobScheduler;
import com.blindtest.service.port.MediaStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reusable deterministic replacements for every external authentication/import dependency. */
@TestConfiguration(proxyBeanMethods = false)
public class DeterministicTestAdaptersConfig {

    /** Captures transactional email instead of calling Resend. */
    @Bean
    @Primary
    public FakeEmailSender fakeEmailSender() {
        return new FakeEmailSender();
    }

    /** Verifies tokens from deterministic in-memory state instead of calling Cloudflare. */
    @Bean
    @Primary
    public FakeTurnstileVerifier fakeTurnstileVerifier() {
        return new FakeTurnstileVerifier();
    }

    /** Produces a valid MP3 fixture instead of starting yt-dlp or FFmpeg. */
    @Bean
    @Primary
    public FakeProcessFactory fakeProcessFactory() {
        return new FakeProcessFactory();
    }

    /** Keeps media objects in memory instead of contacting S3-compatible storage. */
    @Bean
    @Primary
    public MediaStorage inMemoryMediaStorage() {
        return new InMemoryMediaStorage();
    }

    /** Runs scheduled imports synchronously for deterministic full-stack assertions. */
    @Bean
    @Primary
    public ImportJobScheduler sameThreadImportJobScheduler(ImportJobRunner runner) {
        return new SameThreadImportJobScheduler(runner);
    }

    /** Freezes authentication time for repeatable token and expiry behavior. */
    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC);
    }

    /** Returns stable, unique opaque tokens within one test application context. */
    @Bean
    @Primary
    public TokenGenerator deterministicTokenGenerator() {
        AtomicInteger sequence = new AtomicInteger();
        return () -> "test-refresh-token-" + sequence.incrementAndGet();
    }
}

package com.blindtest.testing.e2e;

import com.blindtest.infra.ProcessFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Replaces external-network adapters with deterministic fakes for the isolated full-stack
 * Compose environment (E2E-01). Only active when {@code SPRING_PROFILES_ACTIVE=e2e}, which no
 * production or development deployment sets, so this never affects a real environment.
 */
@Configuration
@Profile("e2e")
public class E2eAdaptersConfig {

    /** Captures transactional email instead of calling Resend. Also injectable as EmailSender. */
    @Bean
    @Primary
    public FakeEmailSender fakeEmailSender() {
        return new FakeEmailSender();
    }

    /** Produces a valid MP3 fixture instead of starting yt-dlp or FFmpeg. */
    @Bean
    @Primary
    public ProcessFactory e2eProcessFactory() {
        return new FakeProcessFactory();
    }
}

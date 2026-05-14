package com.blindtest.config;

import com.blindtest.infra.ProcessFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Application-level Spring beans that don't belong to a more specific config class. */
@Configuration
public class AppConfig {

    /** BCrypt password encoder used for hashing and verifying user passwords. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Factory that delegates to {@link ProcessBuilder} for real process creation. */
    @Bean
    public ProcessFactory processFactory() {
        return command -> new ProcessBuilder(command).start();
    }
}

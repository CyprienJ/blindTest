package com.blindtest.config;

import com.blindtest.infra.ProcessFactory;
import com.blindtest.security.TokenGenerator;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.service.port.LobbyCodeGenerator;
import com.blindtest.store.InMemoryLobbyStore;
import com.blindtest.store.InMemorySessionStore;
import com.blindtest.store.LobbyStore;
import com.blindtest.store.SessionStore;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.UUID;
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
        return command -> new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    /** UTC wall clock used by authentication expiry calculations. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Generates opaque refresh tokens with production randomness. */
    @Bean
    public TokenGenerator tokenGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    /** Generates opaque session-domain identities. */
    @Bean
    public SessionIdentityGenerator sessionIdentityGenerator() {
        return UUID::randomUUID;
    }

    /** Retains live multiplayer lobbies behind the replaceable store port. */
    @Bean
    public LobbyStore lobbyStore() {
        return new InMemoryLobbyStore();
    }

    /** Draws join codes uniformly from the unambiguous lobby alphabet. */
    @Bean
    public LobbyCodeGenerator lobbyCodeGenerator() {
        SecureRandom random = new SecureRandom();
        return () -> {
            StringBuilder code = new StringBuilder(LobbyCode.LENGTH);
            for (int index = 0; index < LobbyCode.LENGTH; index++) {
                code.append(LobbyCode.ALPHABET.charAt(random.nextInt(LobbyCode.ALPHABET.length())));
            }
            return new LobbyCode(code.toString());
        };
    }

    /** Retains canonical active sessions behind the replaceable store port. */
    @Bean
    public SessionStore sessionStore() {
        return new InMemorySessionStore();
    }
}

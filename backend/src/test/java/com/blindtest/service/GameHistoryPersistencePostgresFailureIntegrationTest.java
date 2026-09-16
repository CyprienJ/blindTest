package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedRuntimeException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E-06 (PostgreSQL transient failure): a real TCP proxy, not just a hard-down dependency, sits
 * between the application and PostgreSQL so the connection can be cut mid-write and restored,
 * the way a network blip or brief PostgreSQL restart would behave. Proves the durable
 * game-history write fails cleanly with no partial row while the connection is down, and that
 * retrying the same completed session afterward persists exactly one durable aggregate — never a
 * duplicate.
 *
 * <p>{@code Propagation.NOT_SUPPORTED} opts this class out of {@code @DataJpaTest}'s default
 * single rolled-back test transaction: that default would hold one connection for the whole
 * test method, so cutting connectivity mid-test would permanently poison it and the "recovery"
 * write could never observably succeed on its own connection.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.datasource.hikari.initialization-fail-timeout=0",
        // Toxiproxy's connection cut silently blackholes bytes rather than resetting the TCP
        // connection, so a query already in flight on an already-pooled connection blocks on a
        // plain socket read with no signal it will ever resume. Hikari's connection-timeout only
        // bounds acquiring a connection from the pool, not a read on one already checked out, so
        // without this the cut can hang the test (and a real production connection) forever.
        "spring.datasource.hikari.data-source-properties.socketTimeout=3"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({GameHistoryPersistenceService.class,
        GameHistoryPersistencePostgresFailureIntegrationTest.JsonConfiguration.class})
class GameHistoryPersistencePostgresFailureIntegrationTest {

    private static final int POSTGRESQL_PORT = 5432;
    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").
            withNetwork(NETWORK).
            withNetworkAliases("postgres");

    @Container
    private static final ToxiproxyContainer TOXIPROXY =
            new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0")).
                    withNetwork(NETWORK);

    private static ToxiproxyContainer.ContainerProxy proxy;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        proxy = TOXIPROXY.getProxy(POSTGRES, POSTGRESQL_PORT);
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort()
                + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private GameHistoryPersistenceService service;
    @Autowired
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;

    @AfterEach
    void restoreConnectivity() {
        proxy.setConnectionCut(false);
    }

    @Test
    void connectionLossDuringPersistenceLeavesNoPartialRowAndRetryWritesExactlyOne() {
        proxy.setConnectionCut(true);

        // Depending on exactly where the connection drops, Spring surfaces either a JDBC-layer
        // DataAccessException or a transaction-acquisition TransactionException; both are
        // NestedRuntimeException and both mean the same thing here: a clean, translated failure
        // rather than a raw driver exception or a hang.
        assertThatThrownBy(() -> service.persist(FinishedSessionFixture.finished())).
                isInstanceOf(NestedRuntimeException.class);

        proxy.setConnectionCut(false);
        assertThat(historyRepository.count()).isZero();

        assertThat(service.persist(FinishedSessionFixture.finished())).
                isEqualTo(GameHistoryPersistenceOutcome.CREATED);

        assertThat(historyRepository.count()).isOne();
        var history = historyRepository.findBySessionId(FinishedSessionFixture.SESSION_ID).orElseThrow();
        assertThat(participantRepository.findByGameHistoryId(history.getId())).hasSize(1);
        assertThat(roundRepository.findByGameHistoryIdOrderByRoundIndex(history.getId())).hasSize(5);
    }

    @TestConfiguration
    static class JsonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({GameHistoryPersistenceService.class,
        GameHistoryPersistenceIntegrationTest.JsonConfiguration.class})
class GameHistoryPersistenceIntegrationTest {

    @Autowired
    private GameHistoryPersistenceService service;
    @Autowired
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;

    @Test
    void duplicateCompletionCreatesExactlyOneDurableAggregate() {
        assertThat(service.persist(FinishedSessionFixture.finished())).
                isEqualTo(GameHistoryPersistenceOutcome.CREATED);
        assertThat(service.persist(FinishedSessionFixture.finished())).
                isEqualTo(GameHistoryPersistenceOutcome.ALREADY_EXISTS);

        assertThat(historyRepository.count()).isOne();
        var history = historyRepository.findBySessionId(FinishedSessionFixture.SESSION_ID).
                orElseThrow();
        assertThat(participantRepository.findByGameHistoryId(history.getId())).hasSize(1);
        assertThat(history.getDifficultyLevels()).isEqualTo("2,3");
        assertThat(history.isIncludeUnrated()).isTrue();
        var rounds = roundRepository.findByGameHistoryIdOrderByRoundIndex(history.getId());
        assertThat(rounds).hasSize(5);
        assertThat(rounds).extracting(round -> round.getDifficultySnapshot()).
                containsExactly((short) 2, (short) 2, (short) 3, (short) 3, null);
        assertThat(rounds).extracting(round -> round.getExcerptDurationSeconds()).
                containsOnly(20);
    }

    @TestConfiguration
    static class JsonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

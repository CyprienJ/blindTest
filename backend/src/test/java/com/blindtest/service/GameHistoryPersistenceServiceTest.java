package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.model.entity.HistoryParticipantKind;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameHistoryPersistenceServiceTest {

    private final GameHistoryRepository historyRepository = mock(GameHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GameHistoryPersistenceService service = new GameHistoryPersistenceService(
            historyRepository, userRepository, new ObjectMapper());

    @BeforeEach
    void defaultMissingHistoryAndUser() {
        when(historyRepository.existsBySessionId(FinishedSessionFixture.SESSION_ID)).
                thenReturn(false);
    }

    @Test
    void snapshotsFinishedAggregateWithoutPrivateMediaOrRawGuesses() throws Exception {
        ActiveSessionSnapshot session = FinishedSessionFixture.finished();

        assertThat(service.persist(session)).isEqualTo(GameHistoryPersistenceOutcome.CREATED);

        ArgumentCaptor<GameHistoryEntity> captor =
                ArgumentCaptor.forClass(GameHistoryEntity.class);
        verify(historyRepository).saveAndFlush(captor.capture());
        GameHistoryEntity history = captor.getValue();
        assertThat(history.getSessionId()).isEqualTo(FinishedSessionFixture.SESSION_ID);
        assertThat(history.getHistoryVersion()).isEqualTo("history-v1");
        assertThat(history.getGameMode()).isEqualTo("SOLO");
        assertThat(history.getPlaylistIdSnapshot()).isEqualTo(FinishedSessionFixture.PLAYLIST_ID);
        assertThat(history.getTotalScore()).isEqualTo(1000);
        assertThat(history.getTitlesFound()).isOne();
        assertThat(history.getTimedOutRounds()).isOne();
        assertThat(history.getSkippedRounds()).isEqualTo(3);
        assertThat(history.getStartedAt()).isEqualTo(FinishedSessionFixture.START);
        assertThat(history.getCompletedAt()).isEqualTo(
                Instant.parse("2026-01-31T10:02:46Z"));
        assertThat(history.getRetentionExpiresAt()).isEqualTo(
                Instant.parse("2028-01-31T10:02:46Z"));
        assertThat(history.getParticipants()).singleElement().satisfies(participant -> {
            assertThat(participant.getParticipantKind()).
                    isEqualTo(HistoryParticipantKind.AUTHENTICATED);
            assertThat(participant.getUser()).isNull();
            assertThat(participant.getDisplayName()).isEqualTo("Historical owner");
            assertThat(participant.getFinalScore()).isEqualTo(1000);
            assertThat(participant.getFinalRank()).isOne();
        });
        assertThat(history.getRounds()).hasSize(5).
                extracting(GameHistoryRoundEntity::getRoundIndex).
                containsExactly(0, 1, 2, 3, 4);
        GameHistoryRoundEntity first = history.getRounds().getFirst();
        assertThat(first.getAnswerSnapshotVersion()).isEqualTo("answers-v1");
        var aliases = new ObjectMapper().readTree(first.getAnswerAliasesSnapshot());
        assertThat(aliases.path(GameAnswerType.TITLE.name()).get(0).asText()).
                isEqualTo("First alias");
        assertThat(history.getRounds()).allSatisfy(round -> {
            assertThat(round.getScoringRuleVersion()).isEqualTo("title-linear-v1");
            assertThat(round.getAnswerAliasesSnapshot()).doesNotContain("private/");
        });
    }

    @Test
    void repeatedCompletionReturnsExistingWithoutCreatingAnotherAggregate() {
        when(historyRepository.existsBySessionId(FinishedSessionFixture.SESSION_ID)).
                thenReturn(true);

        assertThat(service.persist(FinishedSessionFixture.finished())).
                isEqualTo(GameHistoryPersistenceOutcome.ALREADY_EXISTS);

        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToPersistAnUnfinishedSession() {
        ActiveSessionSnapshot finished = FinishedSessionFixture.finished();
        ActiveSessionSnapshot unfinished = new ActiveSessionSnapshot(
                finished.sessionId(),
                finished.ownerParticipantId(),
                finished.participants(),
                finished.configuration(),
                com.blindtest.model.game.GameStateSnapshot.created(
                        finished.configuration(),
                        finished.gameState().trackSelection(),
                        finished.gameState().participants()),
                finished.discoveries(),
                finished.processedCommands(),
                finished.version(),
                finished.createdAt(),
                finished.updatedAt(),
                finished.expiresAt(), 0);

        assertThatThrownBy(() -> service.persist(unfinished)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("only a finished session can be persisted");
        verify(historyRepository, never()).saveAndFlush(any());
    }
}

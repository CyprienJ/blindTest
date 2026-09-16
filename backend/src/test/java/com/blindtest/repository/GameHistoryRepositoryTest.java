package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryParticipantEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.model.entity.HistoryParticipantKind;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.RoundOutcome;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class GameHistoryRepositoryTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-08-04T15:00:00Z");

    @Autowired
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsCompleteSnapshotAndReturnsRoundsInAuthoritativeOrder() {
        UserEntity user = userRepository.save(user("history-owner"));
        GameHistoryEntity history = history();
        history.addParticipant(participant(user));
        history.addRound(round(1, "Second", RoundOutcome.TIMEOUT, 0));
        history.addRound(round(0, "First", RoundOutcome.TITLE_FOUND, 1000));

        GameHistoryEntity persisted = historyRepository.saveAndFlush(history);
        entityManager.clear();

        assertThat(historyRepository.findBySessionId(history.getSessionId())).isPresent();
        assertThat(participantRepository.findByGameHistoryId(persisted.getId())).
                singleElement().satisfies(snapshot -> {
                    assertThat(snapshot.getUser().getId()).isEqualTo(user.getId());
                    assertThat(snapshot.getDisplayName()).isEqualTo("Historical owner");
                    assertThat(snapshot.getFinalScore()).isEqualTo(1000);
                });
        assertThat(roundRepository.findByGameHistoryIdOrderByRoundIndex(persisted.getId())).
                extracting(GameHistoryRoundEntity::getTitleSnapshot).
                containsExactly("First", "Second");
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void deletingHistoryCascadesOnlyItsOwnedSnapshots() {
        UserEntity user = userRepository.save(user("cascade-owner"));
        GameHistoryEntity history = history();
        history.addParticipant(participant(user));
        history.addRound(round(0, "Retained until history deletion", RoundOutcome.TIMEOUT, 0));
        UUID historyId = historyRepository.saveAndFlush(history).getId();

        historyRepository.deleteById(historyId);
        historyRepository.flush();

        assertThat(participantRepository.findByGameHistoryId(historyId)).isEmpty();
        assertThat(roundRepository.findByGameHistoryIdOrderByRoundIndex(historyId)).isEmpty();
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private GameHistoryEntity history() {
        GameHistoryEntity history = new GameHistoryEntity();
        history.setSessionId(UUID.randomUUID());
        history.setHistoryVersion("history-v1");
        history.setGameMode("SOLO");
        history.setProtocolVersion((short) 1);
        history.setConfigurationVersion("solo-v1");
        history.setPlaylistIdSnapshot(UUID.randomUUID());
        history.setRequestedTrackCount(5);
        history.setMaximumRoundDurationSeconds(30);
        history.setConfigurationExtensions("{}");
        history.setTotalScore(1000);
        history.setTotalRounds(5);
        history.setTitlesFound(1);
        history.setSkippedRounds(0);
        history.setTimedOutRounds(4);
        history.setStartedAt(STARTED_AT);
        history.setCompletedAt(STARTED_AT.plus(3, ChronoUnit.MINUTES));
        history.setRetentionExpiresAt(STARTED_AT.plus(730, ChronoUnit.DAYS));
        return history;
    }

    private GameHistoryParticipantEntity participant(UserEntity user) {
        GameHistoryParticipantEntity participant = new GameHistoryParticipantEntity();
        participant.setParticipantId(UUID.randomUUID());
        participant.setUser(user);
        participant.setDisplayName("Historical owner");
        participant.setParticipantKind(HistoryParticipantKind.AUTHENTICATED);
        participant.setOwner(true);
        participant.setFinalScore(1000);
        participant.setFinalRank(1);
        return participant;
    }

    private GameHistoryRoundEntity round(
            int index,
            String title,
            RoundOutcome outcome,
            int points) {
        GameHistoryRoundEntity round = new GameHistoryRoundEntity();
        round.setRoundIndex(index);
        round.setTrackIdSnapshot(UUID.randomUUID());
        round.setTitleSnapshot(title);
        round.setArtistSnapshot("Snapshot artist");
        round.setComposerSnapshot("Snapshot composer");
        round.setAlbumSnapshot("Snapshot album");
        round.setAnswerAliasesSnapshot("{\"TITLE\":[\"Alias\"]}");
        round.setAnswerSnapshotVersion("answers-v1");
        round.setExcerptStartSeconds(12);
        round.setOutcome(outcome);
        round.setPoints(points);
        round.setScoringRuleVersion("title-linear-v1");
        round.setStartedAt(STARTED_AT.plus(index * 40L, ChronoUnit.SECONDS));
        round.setDeadline(round.getStartedAt().plus(30, ChronoUnit.SECONDS));
        round.setRevealedAt(round.getStartedAt().plus(1, ChronoUnit.SECONDS));
        round.setCompletedAt(round.getRevealedAt().plus(4, ChronoUnit.SECONDS));
        return round;
    }

    private UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        return user;
    }
}

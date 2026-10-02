package com.blindtest.service;

import com.blindtest.model.dto.CurrentGameSummaryResponse;
import com.blindtest.model.dto.GameSummaryRoundResponse;
import com.blindtest.model.dto.GameSummaryStandingResponse;
import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryParticipantEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.model.entity.GameHistoryRoundResultEntity;
import com.blindtest.model.entity.HistoryParticipantKind;
import com.blindtest.model.game.RoundOutcome;
import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reads durable completed-game summaries only for registered participants of that game. */
@Service
public class GameHistorySummaryService {

    private final GameHistoryRepository historyRepository;
    private final GameHistoryRoundRepository roundRepository;
    private final GameHistoryParticipantRepository participantRepository;

    /** Wires the durable history, round, and participant repositories. */
    public GameHistorySummaryService(
            GameHistoryRepository historyRepository,
            GameHistoryRoundRepository roundRepository,
            GameHistoryParticipantRepository participantRepository) {
        this.historyRepository = historyRepository;
        this.roundRepository = roundRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Returns the caller's view of the durable summary; unknown games and games the account did
     * not play return the same not-found response.
     */
    @Transactional(readOnly = true)
    public CurrentGameSummaryResponse getParticipantSummary(UUID sessionId, UUID accountId) {
        if (sessionId == null || accountId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        GameHistoryEntity history = historyRepository.
                findParticipatedBySessionIdAndAccountId(sessionId, accountId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GameHistoryParticipantEntity> participants =
                participantRepository.findByGameHistoryId(history.getId());
        GameHistoryParticipantEntity caller = participants.stream().
                filter(participant -> participant.getUser() != null
                        && accountId.equals(participant.getUser().getId())).
                findFirst().
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GameSummaryRoundResponse> rounds = roundRepository.
                findByGameHistoryIdOrderByRoundIndex(history.getId()).
                stream().
                map(round -> toRound(round, caller.getParticipantId())).
                toList();
        return new CurrentGameSummaryResponse(
                history.getSessionId(),
                caller.getFinalScore(),
                history.getTotalRounds(),
                Math.toIntExact(rounds.stream().filter(GameSummaryRoundResponse::titleFound).count()),
                history.getSkippedRounds(),
                history.getTimedOutRounds(),
                history.getStartedAt(),
                history.getCompletedAt(),
                rounds,
                history.getGameMode(),
                caller.getParticipantId(),
                participants.stream().
                        sorted(Comparator.comparingInt(GameHistoryParticipantEntity::getFinalRank).
                                thenComparing(GameHistoryParticipantEntity::getDisplayName)).
                        map(participant -> new GameSummaryStandingResponse(
                                participant.getParticipantId(),
                                participant.getDisplayName(),
                                participant.getFinalScore(),
                                participant.getFinalRank(),
                                participant.getParticipantKind() == HistoryParticipantKind.GUEST)).
                        toList());
    }

    /** Solo rounds have no result rows: the round's points and outcome are the owner's. */
    private GameSummaryRoundResponse toRound(GameHistoryRoundEntity round, UUID participantId) {
        Optional<GameHistoryRoundResultEntity> result = round.getResults().stream().
                filter(candidate -> candidate.getParticipantId().equals(participantId)).
                findFirst();
        int points = result.map(GameHistoryRoundResultEntity::getPoints).orElse(round.getPoints());
        boolean titleFound = result.map(GameHistoryRoundResultEntity::isTitleFound).
                orElse(round.getOutcome() == RoundOutcome.TITLE_FOUND);
        return new GameSummaryRoundResponse(
                round.getRoundIndex(),
                round.getOutcome(),
                points,
                round.getTitleSnapshot(),
                round.getArtistSnapshot(),
                round.getComposerSnapshot(),
                round.getAlbumSnapshot(),
                round.getStartedAt(),
                round.getRevealedAt(),
                round.getCompletedAt(),
                titleFound);
    }
}

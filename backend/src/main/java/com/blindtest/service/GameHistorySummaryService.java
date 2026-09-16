package com.blindtest.service;

import com.blindtest.model.dto.CurrentGameSummaryResponse;
import com.blindtest.model.dto.GameSummaryRoundResponse;
import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reads durable completed-game summaries only for their authenticated owner. */
@Service
public class GameHistorySummaryService {

    private final GameHistoryRepository historyRepository;
    private final GameHistoryRoundRepository roundRepository;

    public GameHistorySummaryService(
            GameHistoryRepository historyRepository,
            GameHistoryRoundRepository roundRepository) {
        this.historyRepository = historyRepository;
        this.roundRepository = roundRepository;
    }

    /** Returns the durable summary without revealing whether another owner's game exists. */
    @Transactional(readOnly = true)
    public CurrentGameSummaryResponse getOwnedSummary(UUID sessionId, UUID accountId) {
        if (sessionId == null || accountId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        GameHistoryEntity history = historyRepository.
                findOwnedBySessionIdAndAccountId(sessionId, accountId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var rounds = roundRepository.findByGameHistoryIdOrderByRoundIndex(history.getId()).
                stream().map(this::toRound).toList();
        return new CurrentGameSummaryResponse(
                history.getSessionId(),
                history.getTotalScore(),
                history.getTotalRounds(),
                history.getTitlesFound(),
                history.getSkippedRounds(),
                history.getTimedOutRounds(),
                history.getStartedAt(),
                history.getCompletedAt(),
                rounds);
    }

    private GameSummaryRoundResponse toRound(GameHistoryRoundEntity round) {
        return new GameSummaryRoundResponse(
                round.getRoundIndex(),
                round.getOutcome(),
                round.getPoints(),
                round.getTitleSnapshot(),
                round.getArtistSnapshot(),
                round.getComposerSnapshot(),
                round.getAlbumSnapshot(),
                round.getStartedAt(),
                round.getRevealedAt(),
                round.getCompletedAt());
    }
}

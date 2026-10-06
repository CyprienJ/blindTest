package com.blindtest.model.dto;

import com.blindtest.model.lobby.AudioMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game configuration for a new multiplayer lobby, as for a solo session, plus where the round
 * audio plays.
 *
 * @param audioMode which devices play the audio; {@code null} means every player's
 */
public record CreateLobbyRequest(
        UUID playlistId,
        int trackCount,
        int maxRoundSeconds,
        Map<String, String> extensions,
        List<Integer> difficultyLevels,
        Boolean includeUnrated,
        AudioMode audioMode) {

    /** Returns the game configuration part shared with solo sessions. */
    public CreateSoloSessionRequest gameRequest() {
        return new CreateSoloSessionRequest(
                playlistId, trackCount, maxRoundSeconds, extensions, difficultyLevels, includeUnrated);
    }
}

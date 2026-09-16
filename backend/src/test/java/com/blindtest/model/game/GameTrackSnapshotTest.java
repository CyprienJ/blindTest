package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameTrackSnapshotTest {

    @Test
    void answerSnapshotRejectsMissingCanonicalAnswersAndBlankAliases() {
        assertThatThrownBy(() -> new GameAnswerSnapshot(" ", "Artist", null, null, Map.of())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("title is required");
        assertThatThrownBy(() -> new GameAnswerSnapshot(
                        "Title", "Artist", null, null,
                        Map.of(GameAnswerType.TITLE, List.of(" ")))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("alias is required");
    }

    @Test
    void mediaSnapshotRequiresBothInternalKeyAndVersion() {
        assertThatThrownBy(() -> new GameMediaSnapshot(null, "checksum")).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("objectKey is required");
        assertThatThrownBy(() -> new GameMediaSnapshot("tracks/1/audio.mp3", " ")).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("version is required");
    }

    @Test
    void playbackSnapshotRejectsImpossibleWindows() {
        assertThatThrownBy(() -> new GamePlaybackSnapshot(-1, 180)).
                isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GamePlaybackSnapshot(10, null)).
                isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GamePlaybackSnapshot(180, 180)).
                isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectionRejectsDuplicateTracksAndNonContiguousOrder() {
        GameTrackSnapshot first = snapshot(0);
        GameTrackSnapshot duplicate = new GameTrackSnapshot(
                first.trackId(), 1, first.answers(), first.playback(), first.media());

        assertThatThrownBy(() -> new GameTrackSelection(List.of(first, duplicate))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("track identities must be unique");
        assertThatThrownBy(() -> new GameTrackSelection(List.of(snapshot(1)))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("track play orders must be contiguous");
    }

    private GameTrackSnapshot snapshot(int playOrder) {
        return new GameTrackSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                playOrder,
                new GameAnswerSnapshot("Title", "Artist", null, null, Map.of()),
                new GamePlaybackSnapshot(0, null),
                new GameMediaSnapshot("tracks/1/audio.mp3", "checksum"));
    }
}

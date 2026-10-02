package com.blindtest.model.game;

import static com.blindtest.model.game.GameTestParticipants.SOLO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameDomainPropertyTest {

    private static final UUID PLAYLIST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final Instant DEADLINE = Instant.parse("2026-08-04T12:01:00Z");

    @Test
    void titleScoreIsBoundedAndMonotonicForEveryAllowedDuration() {
        TitleScoreCalculator calculator = new TitleScoreCalculator();

        for (int durationSeconds = GameConfigurationLimits.MINIMUM_ROUND_DURATION_SECONDS;
                durationSeconds <= GameConfigurationLimits.MAXIMUM_ROUND_DURATION_SECONDS;
                durationSeconds += GameConfigurationLimits.ROUND_DURATION_INCREMENT_SECONDS) {
            RoundScoringWindow window = new RoundScoringWindow(
                    DEADLINE, Duration.ofSeconds(durationSeconds));
            int previousScore = TitleScoreCalculator.MAXIMUM_TITLE_POINTS;
            long durationMillis = Duration.ofSeconds(durationSeconds).toMillis();
            for (long elapsedMillis = 0; elapsedMillis < durationMillis; elapsedMillis += 137) {
                int score = calculator.scoreAnswer(
                        AnswerMatchResult.matched(GameAnswerType.TITLE),
                        window.startsAt().plusMillis(elapsedMillis),
                        window,
                        SOLO,
                        RoundScoreState.unscored()).pointsAwarded();
                assertThat(score).isBetween(
                        TitleScoreCalculator.MINIMUM_TITLE_POINTS,
                        TitleScoreCalculator.MAXIMUM_TITLE_POINTS);
                assertThat(score).isLessThanOrEqualTo(previousScore);
                previousScore = score;
            }
            assertThat(calculator.scoreAnswer(
                            AnswerMatchResult.matched(GameAnswerType.TITLE),
                            DEADLINE,
                            window,
                            SOLO,
                            RoundScoreState.unscored()).pointsAwarded()).
                    isZero();
        }
    }

    @Test
    void normalizationIsIdempotentAcrossReproducibleGeneratedUnicodeInputs() {
        AnswerNormalizer normalizer = new AnswerNormalizer();
        Random random = new Random(0xB11D7E57L);
        String[] fragments = {
            "Été", "e\u0301le\u0300ve", "L’", "THE", "東京", "２０２６",
            "AC/DC", "  ", "—", "🎵", "Jean-Jacques", "R.E.M.", "œuvre"
        };

        for (int sample = 0; sample < 2_000; sample++) {
            StringBuilder generated = new StringBuilder();
            int fragmentCount = 1 + random.nextInt(12);
            for (int index = 0; index < fragmentCount; index++) {
                generated.append(fragments[random.nextInt(fragments.length)]);
                generated.append(random.nextBoolean() ? ' ' : '!');
            }
            NormalizedAnswer first = normalizer.normalize(generated.toString());
            NormalizedAnswer second = normalizer.normalize(first.value());

            assertThat(second).isEqualTo(first);
            assertThat(String.join(" ", first.tokens())).isEqualTo(first.value());
        }
    }

    @Test
    void seededSelectionAlwaysPreservesCountUniquenessOrderAndDeterminism() {
        List<GameTrackCandidate> candidates = candidates(40);

        for (int requestedCount : new int[] {5, 15, 30}) {
            GameConfiguration configuration =
                    new GameConfiguration(PLAYLIST_ID, requestedCount, 30);
            for (int seed = 0; seed < 100; seed++) {
                GameTrackSelection first = selector(seed).select(configuration, candidates);
                GameTrackSelection replay = selector(seed).select(configuration, candidates);

                assertThat(first).isEqualTo(replay);
                assertThat(first.tracks()).hasSize(requestedCount);
                assertThat(first.tracks()).extracting(GameTrackSnapshot::trackId).
                        doesNotHaveDuplicates();
                assertThat(first.tracks()).extracting(GameTrackSnapshot::playOrder).
                        containsExactlyElementsOf(
                                java.util.stream.IntStream.range(0, requestedCount).
                                        boxed().toList());
            }
        }
    }

    private GameTrackSelector selector(int seed) {
        Random random = new Random(seed);
        return new GameTrackSelector(random::nextInt);
    }

    private List<GameTrackCandidate> candidates(int count) {
        List<GameTrackCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(new GameTrackCandidate(
                    UUID.fromString(
                            "00000000-0000-0000-0000-" + String.format("%012d", index + 1)),
                    true,
                    new GameAnswerSnapshot(
                            "Title " + index, "Artist " + index, null, null, Map.of()),
                    new GamePlaybackSnapshot(index, 180),
                    new GameMediaSnapshot(
                            "tracks/" + index + "/audio.mp3", "checksum-" + index)));
        }
        return candidates;
    }
}

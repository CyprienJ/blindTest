package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-0019 multiplayer rules on the pure game domain (MP-01). */
class MultiplayerGameDomainTest {

    private static final Instant START = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID PLAYLIST_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final AnswerMatchResult TITLE_MATCH =
            AnswerMatchResult.matched(GameAnswerType.TITLE);

    private final GameStateMachine machine =
            new GameStateMachine(new RoundStateMachine(new TitleScoreCalculator()));

    @ParameterizedTest(name = "{0} participants")
    @ValueSource(ints = {1, 2, 10})
    void revealsOnlyOnceEveryActiveParticipantFoundTheTitle(int count) {
        List<GameParticipantId> players = GameTestParticipants.players(count);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);

        for (int index = 0; index < count; index++) {
            assertThat(round(game).status()).isEqualTo(RoundStatus.PLAYING);
            game = machine.awardTitle(
                    game, players.get(index), TITLE_MATCH, START.plusSeconds(15L + index), active);
        }

        RoundStateSnapshot revealed = round(game);
        assertThat(revealed.status()).isEqualTo(RoundStatus.REVEALED);
        assertThat(revealed.outcome()).contains(RoundOutcome.TITLE_FOUND);
        assertThat(revealed.revealedAt()).contains(START.plusSeconds(15L + count - 1));
        for (int index = 0; index < count; index++) {
            // Everyone scores on the shared curve; finding it first gives no bonus.
            int expected = Math.round(1000f * (45 - index) / 60);
            assertThat(revealed.scoreState().pointsFor(players.get(index))).isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{0} connected need {1} votes")
    @CsvSource({"1, 1", "2, 2", "3, 2", "4, 3", "5, 3", "10, 6"})
    void skipPassesAtSixtyPercentOfConnectedParticipantsRoundedUp(int count, int required) {
        assertThat(SkipVoteRule.requiredVotes(count)).isEqualTo(required);
        List<GameParticipantId> players = GameTestParticipants.players(count);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);

        for (int index = 0; index < required - 1; index++) {
            game = machine.voteSkip(game, players.get(index), START.plusSeconds(5), active);
            assertThat(round(game).status()).isEqualTo(RoundStatus.PLAYING);
        }
        game = machine.voteSkip(game, players.get(required - 1), START.plusSeconds(6), active);

        assertThat(round(game).outcome()).contains(RoundOutcome.SKIPPED);
        assertThat(round(game).revealedAt()).contains(START.plusSeconds(6));
    }

    @Test
    void repeatedSkipVoteCountsOnceAndRepeatedTitleIsRejected() {
        List<GameParticipantId> players = GameTestParticipants.players(3);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);

        game = machine.voteSkip(game, players.get(0), START.plusSeconds(1), active);
        GameStateSnapshot repeated =
                machine.voteSkip(game, players.get(0), START.plusSeconds(2), active);
        assertThat(round(repeated).status()).isEqualTo(RoundStatus.PLAYING);
        assertThat(round(repeated).scoreState()).isEqualTo(round(game).scoreState());

        GameStateSnapshot found =
                machine.awardTitle(game, players.get(1), TITLE_MATCH, START.plusSeconds(3), active);
        assertFailure(
                () -> machine.awardTitle(
                        found, players.get(1), TITLE_MATCH, START.plusSeconds(4), active),
                GameTransitionError.TITLE_ALREADY_FOUND);
    }

    @Test
    void finderMayStillVoteAndAwardsSurviveASkip() {
        List<GameParticipantId> players = GameTestParticipants.players(2);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);

        game = machine.awardTitle(game, players.get(0), TITLE_MATCH, START.plusSeconds(30), active);
        game = machine.voteSkip(game, players.get(0), START.plusSeconds(31), active);
        game = machine.voteSkip(game, players.get(1), START.plusSeconds(32), active);

        assertThat(round(game).outcome()).contains(RoundOutcome.SKIPPED);
        assertThat(round(game).scoreState().pointsFor(players.get(0))).isEqualTo(500);
        assertThat(game.totalScoreFor(players.get(1))).isZero();
    }

    @Test
    void lateCommandsAtTheDeadlineTimeOutWithoutLosingEarlierAwards() {
        List<GameParticipantId> players = GameTestParticipants.players(3);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);
        game = machine.awardTitle(game, players.get(0), TITLE_MATCH, START.plusSeconds(30), active);
        Instant deadline = START.plusSeconds(60);

        GameStateSnapshot lateTitle =
                machine.awardTitle(game, players.get(1), TITLE_MATCH, deadline, active);
        GameStateSnapshot lateSkip = machine.voteSkip(game, players.get(2), deadline, active);
        GameStateSnapshot timedOut = machine.timeoutRound(game, deadline);

        for (GameStateSnapshot resolved : List.of(lateTitle, lateSkip, timedOut)) {
            assertThat(round(resolved).outcome()).contains(RoundOutcome.TIMEOUT);
            assertThat(round(resolved).scoreState().pointsFor(players.get(0))).isEqualTo(500);
            assertThat(round(resolved).scoreState().titleAwarded(players.get(1))).isFalse();
        }
        // A deadline command and the timer race to the same reveal; the loser gets a transition error.
        assertFailure(
                () -> machine.timeoutRound(lateTitle, deadline),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
    }

    @Test
    void disconnectedParticipantsAreIgnoredByCompletionChecks() {
        List<GameParticipantId> players = GameTestParticipants.players(3);
        Set<GameParticipantId> everyone = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 1);
        game = machine.awardTitle(game, players.get(0), TITLE_MATCH, START.plusSeconds(10), everyone);
        game = machine.awardTitle(game, players.get(1), TITLE_MATCH, START.plusSeconds(20), everyone);
        assertThat(round(game).status()).isEqualTo(RoundStatus.PLAYING);

        GameStateSnapshot nobodyLeft = machine.reevaluateRound(game, START.plusSeconds(21), Set.of());
        assertThat(round(nobodyLeft).status()).isEqualTo(RoundStatus.PLAYING);

        Set<GameParticipantId> remaining = Set.of(players.get(0), players.get(1));
        GameStateSnapshot reevaluated =
                machine.reevaluateRound(game, START.plusSeconds(21), remaining);
        assertThat(round(reevaluated).outcome()).contains(RoundOutcome.TITLE_FOUND);
        assertThat(round(reevaluated).revealedAt()).contains(START.plusSeconds(21));
    }

    @Test
    void skipVotesFromDisconnectedParticipantsDoNotCount() {
        List<GameParticipantId> players = GameTestParticipants.players(5);
        GameStateSnapshot game = playingGame(players, 1);
        Set<GameParticipantId> everyone = Set.copyOf(players);
        game = machine.voteSkip(game, players.get(0), START.plusSeconds(1), everyone);
        game = machine.voteSkip(game, players.get(1), START.plusSeconds(2), everyone);

        // Two of five connected is below three; once voter 0 drops, one of four is below three.
        Set<GameParticipantId> withoutVoter = new HashSet<>(everyone);
        withoutVoter.remove(players.get(0));
        game = machine.reevaluateRound(game, START.plusSeconds(3), withoutVoter);
        assertThat(round(game).status()).isEqualTo(RoundStatus.PLAYING);

        // Two of three connected passes.
        Set<GameParticipantId> threeLeft = Set.of(players.get(1), players.get(2), players.get(3));
        game = machine.voteSkip(game, players.get(2), START.plusSeconds(4), threeLeft);
        assertThat(round(game).outcome()).contains(RoundOutcome.SKIPPED);
    }

    @Test
    void rejectsCommandsFromOrAboutUnknownParticipants() {
        List<GameParticipantId> players = GameTestParticipants.players(2);
        GameParticipantId stranger = new GameParticipantId(UUID.randomUUID());
        GameStateSnapshot game = playingGame(players, 1);

        assertFailure(
                () -> machine.awardTitle(
                        game, stranger, TITLE_MATCH, START.plusSeconds(1), Set.copyOf(players)),
                GameTransitionError.UNKNOWN_PARTICIPANT);
        assertFailure(
                () -> machine.voteSkip(
                        game, players.get(0), START.plusSeconds(1), Set.of(players.get(0), stranger)),
                GameTransitionError.UNKNOWN_PARTICIPANT);
        assertFailure(
                () -> machine.reevaluateRound(game, START.plusSeconds(1), Set.of(stranger)),
                GameTransitionError.UNKNOWN_PARTICIPANT);
    }

    @Test
    void finalStandingsShareRanksForEqualScoresAndKeepJoinOrder() {
        List<GameParticipantId> players = GameTestParticipants.players(4);
        Set<GameParticipantId> active = Set.copyOf(players);
        GameStateSnapshot game = playingGame(players, 2);
        // Round 1: players 1 and 2 tie at 750, player 3 earns 500, player 0 nothing.
        game = machine.awardTitle(game, players.get(1), TITLE_MATCH, START.plusSeconds(15), active);
        game = machine.awardTitle(game, players.get(2), TITLE_MATCH, START.plusSeconds(15), active);
        game = machine.awardTitle(game, players.get(3), TITLE_MATCH, START.plusSeconds(30), active);
        game = machine.timeoutRound(game, START.plusSeconds(60));
        game = machine.completeRound(game, START.plusSeconds(64)).game();
        // Round 2: nobody scores.
        Instant secondStart = START.plusSeconds(70);
        game = machine.startRound(game, window(secondStart));
        game = machine.timeoutRound(game, secondStart.plusSeconds(60));
        GameStateSnapshot finished =
                machine.completeRound(game, secondStart.plusSeconds(64)).game();

        GameSummary summary = finished.finalSummary().orElseThrow();
        assertThat(summary.standings()).extracting(ParticipantStanding::participantId).
                containsExactly(players.get(1), players.get(2), players.get(3), players.get(0));
        assertThat(summary.standings()).extracting(ParticipantStanding::rank).
                containsExactly(1, 1, 3, 4);
        assertThat(summary.standingFor(players.get(3)).totalScore()).isEqualTo(500);
        assertThat(summary.standingFor(players.get(1)).titlesFound()).isEqualTo(1);
        assertThat(summary.totalScore()).isEqualTo(2000);
        assertThat(summary.rounds().getFirst().outcome()).isEqualTo(RoundOutcome.TIMEOUT);
        assertThat(summary.rounds().getFirst().points()).isEqualTo(2000);
    }

    @Test
    void soloGameHasOneStandingRankedFirst() {
        GameStateSnapshot game = playingGame(GameTestParticipants.SOLO_LIST, 1);
        game = machine.awardTitle(
                game, GameTestParticipants.SOLO, TITLE_MATCH, START.plusSeconds(15),
                GameTestParticipants.SOLO_ACTIVE);
        GameStateSnapshot finished = machine.completeRound(game, START.plusSeconds(19)).game();

        assertThat(finished.finalSummary().orElseThrow().standings()).containsExactly(
                new ParticipantStanding(GameTestParticipants.SOLO, 750, 1, 1));
    }

    @Test
    void snapshotRejectsInvalidParticipantLists() {
        GameConfiguration configuration = new GameConfiguration(PLAYLIST_ID, 1, 60);
        GameTrackSelection selection = new GameTrackSelection(List.of(RoundStateMachineTest.track(0)));
        GameParticipantId player = GameTestParticipants.SOLO;

        assertThatThrownBy(() -> GameStateSnapshot.created(configuration, selection, List.of())).
                hasMessage("a game needs one to ten participants");
        assertThatThrownBy(() -> GameStateSnapshot.created(
                        configuration, selection, GameTestParticipants.players(11))).
                hasMessage("a game needs one to ten participants");
        assertThatThrownBy(() -> GameStateSnapshot.created(
                        configuration, selection, List.of(player, player))).
                hasMessage("participant identities must be unique");

        RoundStateSnapshot foreignAward = new RoundStateSnapshot(
                RoundStateMachineTest.track(0),
                RoundStatus.PLAYING,
                Optional.of(window(START)),
                Optional.of(RoundPlaybackWindow.from(
                        RoundStateMachineTest.track(0).playback(), window(START))),
                Optional.empty(),
                RoundScoreState.unscored().withTitleAward(
                        new GameParticipantId(UUID.randomUUID()), 500),
                Optional.empty(),
                Optional.empty());
        assertThatThrownBy(() -> new GameStateSnapshot(
                        configuration, selection, List.of(player), GameStatus.PLAYING, 0,
                        Optional.of(foreignAward), List.of(), Optional.empty())).
                hasMessage("round scores must belong to game participants");
    }

    private GameStateSnapshot playingGame(List<GameParticipantId> players, int trackCount) {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < trackCount; index++) {
            tracks.add(RoundStateMachineTest.track(index));
        }
        GameStateSnapshot created = GameStateSnapshot.created(
                new GameConfiguration(PLAYLIST_ID, trackCount, 60),
                new GameTrackSelection(tracks),
                players);
        return machine.startRound(machine.start(created), window(START));
    }

    private RoundScoringWindow window(Instant startsAt) {
        return new RoundScoringWindow(startsAt.plusSeconds(60), Duration.ofSeconds(60));
    }

    private RoundStateSnapshot round(GameStateSnapshot game) {
        return game.currentRound().orElseThrow();
    }

    private void assertFailure(Runnable action, GameTransitionError expected) {
        assertThatThrownBy(action::run).
                isInstanceOfSatisfying(GameTransitionException.class, failure ->
                        assertThat(failure.error()).isEqualTo(expected));
    }
}

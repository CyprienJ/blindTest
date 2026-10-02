package com.blindtest.model.game;

import static com.blindtest.model.game.GameTestParticipants.SOLO;
import static com.blindtest.model.game.GameTestParticipants.SOLO_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RoundTransitionMatrixTest {

    private static final Instant ROUND_START = Instant.parse("2026-08-04T12:00:00Z");
    private static final RoundScoringWindow WINDOW =
            new RoundScoringWindow(ROUND_START.plusSeconds(30), Duration.ofSeconds(30));

    private final RoundStateMachine machine =
            new RoundStateMachine(new TitleScoreCalculator());

    @ParameterizedTest(name = "{0} from {1}: allowed={2}")
    @MethodSource("transitionMatrix")
    void enforcesTheCompleteRoundTransitionMatrix(
            RoundAction action,
            RoundStatus sourceStatus,
            boolean allowed) {
        RoundStateSnapshot source = stateAt(sourceStatus);

        if (allowed) {
            RoundStateSnapshot result = action.apply(machine, source);
            assertThat(result.status()).isEqualTo(action.targetStatus());
        } else {
            assertThatThrownBy(() -> action.apply(machine, source)).
                    isInstanceOfSatisfying(GameTransitionException.class, failure ->
                            assertThat(failure.error()).
                                    isEqualTo(GameTransitionError.ILLEGAL_ROUND_TRANSITION));
        }
    }

    private RoundStateSnapshot stateAt(RoundStatus status) {
        RoundStateSnapshot preparing = RoundStateSnapshot.preparing(
                RoundStateMachineTest.track(0));
        if (status == RoundStatus.PREPARING) {
            return preparing;
        }
        RoundStateSnapshot playing = machine.start(preparing, WINDOW);
        if (status == RoundStatus.PLAYING) {
            return playing;
        }
        RoundStateSnapshot revealed = machine.voteSkip(playing, SOLO, ROUND_START.plusSeconds(1), SOLO_ACTIVE);
        if (status == RoundStatus.REVEALED) {
            return revealed;
        }
        return machine.complete(revealed, ROUND_START.plusSeconds(2));
    }

    private static Stream<Arguments> transitionMatrix() {
        return Stream.of(RoundAction.values()).flatMap(action ->
                Stream.of(RoundStatus.values()).map(status -> Arguments.of(
                        action, status, status == action.allowedSource())));
    }

    private enum RoundAction {
        START(RoundStatus.PREPARING, RoundStatus.PLAYING) {
            @Override
            RoundStateSnapshot apply(RoundStateMachine machine, RoundStateSnapshot source) {
                return machine.start(source, WINDOW);
            }
        },
        TITLE(RoundStatus.PLAYING, RoundStatus.REVEALED) {
            @Override
            RoundStateSnapshot apply(RoundStateMachine machine, RoundStateSnapshot source) {
                return machine.awardTitle(
                        source,
                        SOLO,
                        AnswerMatchResult.matched(GameAnswerType.TITLE),
                        ROUND_START.plusSeconds(1),
                        SOLO_ACTIVE);
            }
        },
        SKIP(RoundStatus.PLAYING, RoundStatus.REVEALED) {
            @Override
            RoundStateSnapshot apply(RoundStateMachine machine, RoundStateSnapshot source) {
                return machine.voteSkip(source, SOLO, ROUND_START.plusSeconds(1), SOLO_ACTIVE);
            }
        },
        TIMEOUT(RoundStatus.PLAYING, RoundStatus.REVEALED) {
            @Override
            RoundStateSnapshot apply(RoundStateMachine machine, RoundStateSnapshot source) {
                return machine.timeout(source, WINDOW.deadline());
            }
        },
        COMPLETE(RoundStatus.REVEALED, RoundStatus.COMPLETED) {
            @Override
            RoundStateSnapshot apply(RoundStateMachine machine, RoundStateSnapshot source) {
                return machine.complete(source, ROUND_START.plusSeconds(2));
            }
        };

        private final RoundStatus allowedSource;
        private final RoundStatus targetStatus;

        RoundAction(RoundStatus allowedSource, RoundStatus targetStatus) {
            this.allowedSource = allowedSource;
            this.targetStatus = targetStatus;
        }

        RoundStatus allowedSource() {
            return allowedSource;
        }

        RoundStatus targetStatus() {
            return targetStatus;
        }

        abstract RoundStateSnapshot apply(
                RoundStateMachine machine,
                RoundStateSnapshot source);
    }
}

package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MatchStateMachineTest {
    @Test
    void exercisesTheLifecycleContractForEveryStateAndEventWithoutRepeatingEffects() {
        var paths = Map.ofEntries(
            Map.entry(MatchPhase.RESERVED, List.<MatchLifecycleEvent>of()),
            Map.entry(MatchPhase.LOADING, List.of(MatchLifecycleEvent.START)),
            Map.entry(MatchPhase.SYNCHRONIZING, List.of(MatchLifecycleEvent.START, MatchLifecycleEvent.LOADING_READY)),
            Map.entry(
                MatchPhase.PREVIEW,
                List.of(MatchLifecycleEvent.START, MatchLifecycleEvent.LOADING_READY, MatchLifecycleEvent.START_PREVIEW)
            ),
            Map.entry(
                MatchPhase.ACTION,
                List.of(MatchLifecycleEvent.START, MatchLifecycleEvent.LOADING_READY, MatchLifecycleEvent.START_ACTION)
            ),
            Map.entry(
                MatchPhase.SETTLING,
                List.of(
                    MatchLifecycleEvent.START,
                    MatchLifecycleEvent.LOADING_READY,
                    MatchLifecycleEvent.START_ACTION,
                    MatchLifecycleEvent.CLOSE_TURN
                )
            ),
            Map.entry(
                MatchPhase.COMPLETION,
                List.of(
                    MatchLifecycleEvent.START,
                    MatchLifecycleEvent.LOADING_READY,
                    MatchLifecycleEvent.START_ACTION,
                    MatchLifecycleEvent.SCENARIO_ACCEPTED
                )
            ),
            Map.entry(MatchPhase.TERMINATING, List.of(MatchLifecycleEvent.TERMINATE)),
            Map.entry(
                MatchPhase.RESULT_SETTLEMENT,
                List.of(MatchLifecycleEvent.TERMINATE, MatchLifecycleEvent.SETTLE_RESULT)
            ),
            Map.entry(
                MatchPhase.RESULT_DELIVERY,
                List.of(MatchLifecycleEvent.TERMINATE, MatchLifecycleEvent.DELIVER_RESULT)
            ),
            Map.entry(
                MatchPhase.ROOM_RETURN,
                List.of(
                    MatchLifecycleEvent.TERMINATE,
                    MatchLifecycleEvent.DELIVER_RESULT,
                    MatchLifecycleEvent.RETURN_ROOM
                )
            ),
            Map.entry(MatchPhase.CLOSED, List.of(MatchLifecycleEvent.CLOSE))
        );

        for (var phase : MatchPhase.values()) {
            for (var event : MatchLifecycleEvent.values()) {
                var machine = new MatchStateMachine();
                paths.get(phase).forEach(step -> machine.on(step, OptionalLong.of(100)));
                assertEquals(phase, machine.phase());
                long revision = machine.revision();
                var transition = machine.on(event, OptionalLong.of(200));
                var expected = expected(phase, event);

                assertEquals(phase, transition.before());

                if (expected == null) {
                    var decision = phase == MatchPhase.CLOSED || machine.lastEvent() == event
                        ? MatchTransitionDecision.IGNORED : MatchTransitionDecision.REJECTED;
                    assertEquals(decision, transition.decision());
                    assertEquals(phase, machine.phase());
                    assertEquals(revision, machine.revision());
                    assertEquals(List.of(), transition.effects());
                } else {
                    assertEquals(MatchTransitionDecision.APPLIED, transition.decision());
                    assertEquals(expected, transition.after());
                    assertEquals(expected, machine.phase());
                    assertEquals(revision + 1, transition.revision());
                    assertEquals(200, machine.deadlineNanos());
                    assertFalse(transition.effects().isEmpty());
                    assertThrows(UnsupportedOperationException.class, () -> transition.effects().clear());
                }
            }
        }
    }

    @Test
    void keepsDeadlineWhenNoNewDeadlineIsProvidedAndClosesOnlyOnce() {
        var machine = new MatchStateMachine();
        assertEquals(0, machine.deadlineNanos());
        assertEquals(MatchLifecycleEvent.START, machine.lastEvent());
        machine.on(MatchLifecycleEvent.START, OptionalLong.of(100));
        machine.on(MatchLifecycleEvent.TERMINATE, OptionalLong.empty());

        assertEquals(100, machine.deadlineNanos());
        assertEquals(2, machine.revision());
        machine.on(MatchLifecycleEvent.CLOSE, OptionalLong.empty());
        var duplicate = machine.on(MatchLifecycleEvent.CLOSE, OptionalLong.of(500));

        assertEquals(MatchTransitionDecision.IGNORED, duplicate.decision());
        assertEquals(3, machine.revision());
        assertEquals(100, machine.deadlineNanos());
    }

    private MatchPhase expected(MatchPhase phase, MatchLifecycleEvent event) {
        if (phase == MatchPhase.CLOSED) {
            return null;
        }

        if (event == MatchLifecycleEvent.CLOSE) {
            return MatchPhase.CLOSED;
        }

        if (event == MatchLifecycleEvent.TERMINATE) {
            return List.of(
                MatchPhase.TERMINATING,
                MatchPhase.RESULT_SETTLEMENT,
                MatchPhase.RESULT_DELIVERY,
                MatchPhase.ROOM_RETURN
            ).contains(phase) ? null : MatchPhase.TERMINATING;
        }

        return switch (phase) {
            case RESERVED -> event == MatchLifecycleEvent.START ? MatchPhase.LOADING : null;
            case LOADING -> event == MatchLifecycleEvent.LOADING_READY ? MatchPhase.SYNCHRONIZING : null;
            case SYNCHRONIZING -> switch (event) {
                case START_ACTION -> MatchPhase.ACTION;
                case START_PREVIEW -> MatchPhase.PREVIEW;
                default -> null;
            };
            case ACTION -> switch (event) {
                case CLOSE_TURN -> MatchPhase.SETTLING;
                case SCENARIO_ACCEPTED -> MatchPhase.COMPLETION;
                default -> null;
            };
            case SETTLING -> switch (event) {
                case SCENARIO_ACCEPTED -> MatchPhase.COMPLETION;
                case NEXT_TURN -> MatchPhase.ACTION;
                default -> null;
            };
            case COMPLETION -> event == MatchLifecycleEvent.NEXT_TURN ? MatchPhase.ACTION : null;
            case TERMINATING -> switch (event) {
                case SETTLE_RESULT -> MatchPhase.RESULT_SETTLEMENT;
                case DELIVER_RESULT -> MatchPhase.RESULT_DELIVERY;
                default -> null;
            };
            case RESULT_SETTLEMENT -> event == MatchLifecycleEvent.DELIVER_RESULT ? MatchPhase.RESULT_DELIVERY : null;
            case RESULT_DELIVERY -> event == MatchLifecycleEvent.RETURN_ROOM ? MatchPhase.ROOM_RETURN : null;
            default -> null;
        };
    }
}

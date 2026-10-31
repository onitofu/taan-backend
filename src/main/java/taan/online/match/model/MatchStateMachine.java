package taan.online.match.model;

import java.util.List;
import java.util.OptionalLong;

final class MatchStateMachine {
    private MatchPhase phase = MatchPhase.RESERVED;
    private long revision;
    private MatchLifecycleEvent lastEvent = MatchLifecycleEvent.START;
    private long deadlineNanos;

    public MatchPhase phase() {
        return phase;
    }

    public MatchLifecycleEvent lastEvent() {
        return lastEvent;
    }

    public long revision() {
        return revision;
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    public MatchLifecycleTransition on(MatchLifecycleEvent event, OptionalLong deadline) {
        var before = phase;
        var next = next(event);

        if (next == null) {
            var decision = phase == MatchPhase.CLOSED || lastEvent == event
                ? MatchTransitionDecision.IGNORED : MatchTransitionDecision.REJECTED;
            return new MatchLifecycleTransition(before, before, revision, decision, List.of());
        }

        lastEvent = event;
        phase = next;
        revision++;
        deadline.ifPresent(value -> deadlineNanos = value);
        return new MatchLifecycleTransition(before, phase, revision, MatchTransitionDecision.APPLIED, effects(event));
    }

    private MatchPhase next(MatchLifecycleEvent event) {
        if (phase == MatchPhase.CLOSED) {
            return null;
        }

        return switch (event) {
            case START -> phase == MatchPhase.RESERVED ? MatchPhase.LOADING : null;
            case LOADING_READY -> phase == MatchPhase.LOADING ? MatchPhase.SYNCHRONIZING : null;
            case START_ACTION -> phase == MatchPhase.SYNCHRONIZING ? MatchPhase.ACTION : null;
            case START_PREVIEW -> phase == MatchPhase.SYNCHRONIZING ? MatchPhase.PREVIEW : null;
            case SCENARIO_ACCEPTED -> phase == MatchPhase.ACTION || phase == MatchPhase.SETTLING
                ? MatchPhase.COMPLETION : null;
            case CLOSE_TURN -> phase == MatchPhase.ACTION ? MatchPhase.SETTLING : null;
            case NEXT_TURN -> phase == MatchPhase.SETTLING || phase == MatchPhase.COMPLETION ? MatchPhase.ACTION : null;
            case TERMINATE -> switch (phase) {
                case TERMINATING, RESULT_SETTLEMENT, RESULT_DELIVERY, ROOM_RETURN -> null;
                default -> MatchPhase.TERMINATING;
            };
            case SETTLE_RESULT -> phase == MatchPhase.TERMINATING ? MatchPhase.RESULT_SETTLEMENT : null;
            case DELIVER_RESULT -> phase == MatchPhase.TERMINATING || phase == MatchPhase.RESULT_SETTLEMENT
                ? MatchPhase.RESULT_DELIVERY : null;
            case RETURN_ROOM -> phase == MatchPhase.RESULT_DELIVERY ? MatchPhase.ROOM_RETURN : null;
            case CLOSE -> MatchPhase.CLOSED;
        };
    }

    private List<MatchLifecycleEffect> effects(MatchLifecycleEvent event) {
        return switch (event) {
            case START, LOADING_READY -> List.of(MatchLifecycleEffect.PUBLISH_BOOTSTRAP);
            case START_ACTION, START_PREVIEW, CLOSE_TURN, SCENARIO_ACCEPTED ->
                List.of(MatchLifecycleEffect.ARM_DEADLINE);
            case NEXT_TURN -> List.of(MatchLifecycleEffect.PUBLISH_TURN, MatchLifecycleEffect.ARM_DEADLINE);
            case TERMINATE -> List.of(MatchLifecycleEffect.STOP_GAMEPLAY);
            case SETTLE_RESULT -> List.of(MatchLifecycleEffect.SETTLE_RESULT);
            case DELIVER_RESULT -> List.of(MatchLifecycleEffect.DELIVER_RESULT);
            case RETURN_ROOM -> List.of(MatchLifecycleEffect.RETURN_ROOM);
            case CLOSE -> List.of(MatchLifecycleEffect.CLEANUP);
        };
    }
}

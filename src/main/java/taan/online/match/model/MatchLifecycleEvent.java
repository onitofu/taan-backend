package taan.online.match.model;

public enum MatchLifecycleEvent {
    START,
    LOADING_READY,
    START_ACTION,
    START_PREVIEW,
    SCENARIO_ACCEPTED,
    CLOSE_TURN,
    NEXT_TURN,
    TERMINATE,
    SETTLE_RESULT,
    DELIVER_RESULT,
    RETURN_ROOM,
    CLOSE
}

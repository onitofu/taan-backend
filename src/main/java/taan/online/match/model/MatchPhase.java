package taan.online.match.model;

public enum MatchPhase {
    RESERVED,
    LOADING,
    SYNCHRONIZING,
    PREVIEW,
    ACTION,
    SETTLING,
    COMPLETION,
    TERMINATING,
    RESULT_SETTLEMENT,
    RESULT_DELIVERY,
    ROOM_RETURN,
    CLOSED
}

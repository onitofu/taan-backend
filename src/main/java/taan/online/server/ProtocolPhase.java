package taan.online.server;

public enum ProtocolPhase {
    INITIAL,
    PRELIMINARY,
    AWAITING_LOGIN,
    AUTHENTICATED,
    CHARACTER_SELECTED,
    CHANNEL_SELECTED,
    LOBBY,
    ROOM,
    TERMINATED
}

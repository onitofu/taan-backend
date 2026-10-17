package taan.online.server;

enum ProtocolDecision {
    PROCESS,
    IGNORE,
    REJECT,
    CONTINUE,
    CLOSE_AFTER_REPLY
}

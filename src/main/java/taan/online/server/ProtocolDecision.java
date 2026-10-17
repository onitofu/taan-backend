package taan.online.server;

enum ProtocolDecision {
    PROCESS,
    IGNORE,
    REJECT,
    CONTINUE,
    CLOSE_WITHOUT_REPLY,
    CLOSE_AFTER_REPLY
}

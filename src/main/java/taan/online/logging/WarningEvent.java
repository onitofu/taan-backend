package taan.online.logging;

public enum WarningEvent {
    FRAME_REJECTED("protocol.frame.rejected"),
    LIMIT_EXCEEDED("server.limit.exceeded");

    private final String name;

    WarningEvent(String name) {
        this.name = name;
    }

    String eventName() {
        return name;
    }
}

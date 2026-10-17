package taan.online.lobby.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum LobbyLogEvent implements LogEvent {
    ENTERED("lobby.entered", Level.DEBUG, "Lobby entry processed"),
    LEFT("lobby.left", Level.DEBUG, "Lobby leave processed"),
    FAILED("lobby.request.failed", Level.ERROR, "Lobby request failed");

    private final String eventName;
    private final Level level;
    private final String message;

    LobbyLogEvent(String eventName, Level level, String message) {
        this.eventName = eventName;
        this.level = level;
        this.message = message;
    }

    @Override
    public String eventName() {
        return eventName;
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public String message() {
        return message;
    }
}

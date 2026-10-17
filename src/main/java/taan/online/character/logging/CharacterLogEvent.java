package taan.online.character.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum CharacterLogEvent implements LogEvent {
    NAME_CHECKED("character.name.checked", Level.DEBUG, "Character name checked"),
    NAME_CHECK_FAILED("character.name.check.failed", Level.ERROR, "Character name check failed"),
    CREATION_SUCCEEDED("character.creation.succeeded", Level.INFO, "Character created"),
    CREATION_REJECTED("character.creation.rejected", Level.INFO, "Character creation rejected"),
    CREATION_FAILED("character.creation.failed", Level.ERROR, "Character creation failed"),
    CATALOG_LOADED("character.catalog.loaded", Level.DEBUG, "Character catalog loaded"),
    REQUEST_CANCELLED("character.request.cancelled", Level.DEBUG, "Queued character request cancelled");

    private final String eventName;
    private final Level level;
    private final String message;

    CharacterLogEvent(String eventName, Level level, String message) {
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

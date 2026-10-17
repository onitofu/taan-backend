package taan.online.account.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum AccountLogEvent implements LogEvent {
    CHECK_COMPLETED("account.check.completed", Level.DEBUG, "Account availability checked"),
    CHECK_FAILED("account.check.failed", Level.ERROR, "Account operation failed"),
    REGISTRATION_SUCCEEDED("account.registration.succeeded", Level.INFO, "Account created"),
    REGISTRATION_REJECTED("account.registration.rejected", Level.INFO, "Registration rejected"),
    REGISTRATION_FAILED("account.registration.failed", Level.ERROR, "Account operation failed"),
    REQUEST_CANCELLED("account.request.cancelled", Level.DEBUG, "Queued account request cancelled");

    private final String eventName;
    private final Level level;
    private final String message;

    AccountLogEvent(String eventName, Level level, String message) {
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

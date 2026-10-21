package taan.online.session.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum SessionLogEvent implements LogEvent {
    LOGIN_SUCCEEDED("account.login.succeeded", Level.INFO, "Account authenticated"),
    LOGIN_REJECTED("account.login.rejected", Level.INFO, "Login rejected"),
    LOGIN_FAILED("account.login.failed", Level.ERROR, "Login failed"),
    LOGIN_IGNORED("account.login.ignored", Level.DEBUG, "Login ignored"),
    LOGIN_CANCELLED("account.login.cancelled", Level.DEBUG, "Queued login cancelled"),
    BALANCES_REFRESHED("session.balances.refreshed", Level.DEBUG, "Balances refreshed"),
    BALANCES_REFRESH_IGNORED("session.balances.refresh.ignored", Level.DEBUG, "Balance refresh ignored"),
    BALANCES_REFRESH_FAILED("session.balances.refresh.failed", Level.ERROR, "Balance refresh failed"),
    ENDED("session.ended", Level.INFO, "Authenticated session ended");

    private final String eventName;
    private final Level level;
    private final String message;

    SessionLogEvent(String eventName, Level level, String message) {
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

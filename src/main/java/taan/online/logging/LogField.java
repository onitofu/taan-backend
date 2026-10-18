package taan.online.logging;

import java.net.SocketAddress;
import java.util.UUID;
import org.slf4j.spi.LoggingEventBuilder;

public final class LogField {
    private static final int MAX_TEXT_LENGTH = 256;

    private final String name;
    private final Object value;

    private LogField(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public static LogField port(int value) {
        return new LogField("port", value);
    }

    public static LogField setting(String value) {
        return new LogField("setting", value);
    }

    public static LogField connectionId(UUID value) {
        return new LogField("connectionId", value);
    }

    public static LogField sessionId(UUID value) {
        return new LogField("sessionId", value);
    }

    public static LogField operationId(UUID value) {
        return new LogField("operationId", value);
    }

    public static LogField accountId(long value) {
        return new LogField("accountId", value);
    }

    public static LogField characterId(long value) {
        return new LogField("characterId", value);
    }

    public static LogField sourceInventoryId(long value) {
        return new LogField("sourceInventoryId", value);
    }

    public static LogField destinationInventoryId(long value) {
        return new LogField("destinationInventoryId", value);
    }

    public static LogField instanceId(long value) {
        return new LogField("instanceId", value);
    }

    public static LogField channelId(int value) {
        return new LogField("channelId", value);
    }

    public static LogField roomCount(int value) {
        return new LogField("roomCount", value);
    }

    public static LogField recipientSessionId(UUID value) {
        return new LogField("recipientSessionId", value);
    }

    public static LogField previousSlotIndex(int value) {
        return new LogField("previousSlotIndex", value);
    }

    public static LogField weaponId(int value) {
        return new LogField("weaponId", value);
    }

    public static LogField ready(boolean value) {
        return new LogField("ready", value);
    }

    public static LogField team(String value) {
        return new LogField("team", value);
    }

    public static LogField slotIndex(int value) {
        return new LogField("slotIndex", value);
    }

    public static LogField roomId(int value) {
        return new LogField("roomId", value);
    }

    public static LogField previousChannelId(Integer value) {
        return new LogField("previousChannelId", value);
    }

    public static LogField population(int value) {
        return new LogField("population", value);
    }

    public static LogField durationMs(long value) {
        return new LogField("durationMs", value);
    }

    public static LogField reason(String value) {
        return new LogField("reason", value);
    }

    public static LogField outcome(String value) {
        return new LogField("outcome", value);
    }

    public static LogField packetType(int value) {
        return new LogField("type", Integer.toUnsignedString(value, 16));
    }

    public static LogField rootBytes(int value) {
        return new LogField("rootBytes", value);
    }

    public static LogField remoteAddress(SocketAddress value) {
        return new LogField("remoteAddress", value == null ? null : value.toString());
    }

    void addTo(LoggingEventBuilder entry) {
        if (value != null) {
            entry.addKeyValue(name, value instanceof String text ? sanitize(text) : value);
        }
    }

    private static String sanitize(String text) {
        var result = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029') {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                result.append(character);
            }

            if (result.length() > MAX_TEXT_LENGTH) {
                int end = MAX_TEXT_LENGTH - 1;

                if (Character.isHighSurrogate(result.charAt(end - 1))) {
                    end--;
                }

                return result.substring(0, end) + "…";
            }
        }

        return result.toString();
    }
}

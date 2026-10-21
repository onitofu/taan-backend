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

    public static LogField goldBefore(Long value) {
        return new LogField("goldBefore", value);
    }

    public static LogField goldAfter(Long value) {
        return new LogField("goldAfter", value);
    }

    public static LogField goldDelta(Long value) {
        return new LogField("goldDelta", value);
    }

    public static LogField cardKind(String value) {
        return new LogField("cardKind", value);
    }

    public static LogField cardAmount(Long value) {
        return new LogField("cardAmount", value);
    }

    public static LogField cardIndex(int value) {
        return new LogField("cardIndex", value);
    }

    public static LogField experienceBefore(Long value) {
        return new LogField("experienceBefore", value);
    }

    public static LogField experienceAfter(Long value) {
        return new LogField("experienceAfter", value);
    }

    public static LogField experienceDelta(Long value) {
        return new LogField("experienceDelta", value);
    }

    public static LogField levelBefore(Integer value) {
        return new LogField("levelBefore", value);
    }

    public static LogField levelAfter(Integer value) {
        return new LogField("levelAfter", value);
    }

    public static LogField killMedal(boolean value) {
        return new LogField("killMedal", value);
    }

    public static LogField damage(long value) {
        return new LogField("damage", value);
    }

    public static LogField forceMedal(boolean value) {
        return new LogField("forceMedal", value);
    }

    public static LogField effectiveWeaponId(Integer value) {
        return new LogField("effectiveWeaponId", value);
    }

    public static LogField lootPickerScalar(int value) {
        return new LogField("lootPickerScalar", value);
    }

    public static LogField energyBefore(int value) {
        return new LogField("energyBefore", value);
    }

    public static LogField itemId(int value) {
        return new LogField("itemId", value);
    }

    public static LogField inventoryIndex(Integer value) {
        return new LogField("inventoryIndex", value);
    }

    public static LogField grantSequence(Long value) {
        return new LogField("grantSequence", value);
    }

    public static LogField boxEntityId(long value) {
        return new LogField("boxEntityId", Long.toUnsignedString(value));
    }

    public static LogField activeBoxCount(int value) {
        return new LogField("activeBoxCount", value);
    }

    public static LogField boxAttemptBudget(int value) {
        return new LogField("boxAttemptBudget", value);
    }

    public static LogField energyAfter(int value) {
        return new LogField("energyAfter", value);
    }

    public static LogField headerEnergyGrant(int value) {
        return new LogField("headerEnergyGrant", value);
    }

    public static LogField damageEnergyMaximum(int value) {
        return new LogField("damageEnergyMaximum", value);
    }

    public static LogField winningTeam(String value) {
        return new LogField("winningTeam", value);
    }

    public static LogField outcomeCause(String value) {
        return new LogField("outcomeCause", value);
    }

    public static LogField round(long value) {
        return new LogField("round", value);
    }

    public static LogField windRaw(int value) {
        return new LogField("windRaw", value);
    }

    public static LogField awaitingCount(int value) {
        return new LogField("awaitingCount", value);
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

    public static LogField channelKind(int value) {
        return new LogField("channelKind", value);
    }

    public static LogField admissionId(UUID value) {
        return new LogField("admissionId", value);
    }

    public static LogField scenarioHash(String value) {
        return new LogField("scenarioHash", value);
    }

    public static LogField commandCount(int value) {
        return new LogField("commandCount", value);
    }

    public static LogField worldRevision(long value) {
        return new LogField("worldRevision", value);
    }

    public static LogField changedCells(int value) {
        return new LogField("changedCells", value);
    }

    public static LogField processedDeaths(int value) {
        return new LogField("processedDeaths", value);
    }

    public static LogField turnRevision(long value) {
        return new LogField("turnRevision", value);
    }

    public static LogField roomCount(int value) {
        return new LogField("roomCount", value);
    }

    public static LogField targetSessionId(UUID value) {
        return new LogField("targetSessionId", value);
    }

    public static LogField targetAccountId(long value) {
        return new LogField("targetAccountId", value);
    }

    public static LogField targetCharacterId(long value) {
        return new LogField("targetCharacterId", value);
    }

    public static LogField recipientSessionId(UUID value) {
        return new LogField("recipientSessionId", value);
    }

    public static LogField previousSlotIndex(int value) {
        return new LogField("previousSlotIndex", value);
    }

    public static LogField mapId(int value) {
        return new LogField("mapId", value);
    }

    public static LogField roomMode(String value) {
        return new LogField("roomMode", value);
    }

    public static LogField roomRule(String value) {
        return new LogField("roomRule", value);
    }

    public static LogField attackKind(int value) {
        return new LogField("attackKind", value);
    }

    public static LogField weaponId(Integer value) {
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

    public static LogField selectionId(UUID value) {
        return new LogField("selectionId", value);
    }

    public static LogField matchId(UUID value) {
        return new LogField("matchId", value);
    }

    public static LogField matchRevision(long value) {
        return new LogField("matchRevision", value);
    }

    public static LogField commandType(String value) {
        return new LogField("commandType", value);
    }

    public static LogField roomGeneration(UUID value) {
        return new LogField("roomGeneration", value);
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

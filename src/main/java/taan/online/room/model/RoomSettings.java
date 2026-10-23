package taan.online.room.model;

import java.util.Objects;

public record RoomSettings(RoomTitle title, RoomMode mode, RoomRule rule, int mapId, int capacity) {
    public RoomSettings {
        Objects.requireNonNull(title);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(rule);

        if (capacity != 8 || mapId < 1 || mapId > 65535 || mode.usesItems() != (rule != RoomRule.BASIC)) {
            throw new IllegalArgumentException("Unsupported room settings");
        }
    }
}

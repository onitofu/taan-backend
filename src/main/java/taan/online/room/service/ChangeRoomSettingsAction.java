package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.ChangeRoomSettingsResult;

public final class ChangeRoomSettingsAction {
    private final RoomRegistry rooms;

    public ChangeRoomSettingsAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public ChangeRoomSettingsResult execute(UUID id, int mapId, int gameType, int rule) {
        return rooms.changeSettings(id, mapId, gameType, rule);
    }

    public ChangeRoomSettingsResult changeMap(UUID id, int mapId) {
        return rooms.changeMap(id, mapId);
    }
}

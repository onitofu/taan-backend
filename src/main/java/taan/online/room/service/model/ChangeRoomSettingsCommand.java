package taan.online.room.service.model;

import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;

public record ChangeRoomSettingsCommand(int mapId, RoomMode mode, RoomRule rule) {
}

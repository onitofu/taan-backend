package taan.online.room.service.model;

import java.util.UUID;
import taan.online.room.model.RoomId;

public record RoomEntryTarget(RoomId id, UUID generation, int channelId) {
}

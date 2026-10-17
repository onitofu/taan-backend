package taan.online.room.service.model;

import java.util.UUID;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSettings;

public record RoomSnapshot(RoomId id, UUID generation, int channelId, RoomSettings settings, RoomParticipant owner) {
}

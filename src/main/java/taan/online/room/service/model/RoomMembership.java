package taan.online.room.service.model;

import java.util.UUID;
import taan.online.room.model.RoomId;

public record RoomMembership(UUID sessionId, RoomId roomId, UUID generation, int channelId) {
}

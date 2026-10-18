package taan.online.room.service.model;

import java.util.UUID;
import taan.online.room.model.RoomId;

public record RoomSelectionToken(RoomId roomId, UUID generation, UUID selectionId) {
}

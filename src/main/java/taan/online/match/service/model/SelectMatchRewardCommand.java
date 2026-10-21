package taan.online.match.service.model;

import java.util.UUID;
import taan.online.room.service.model.RoomMatchContext;

public record SelectMatchRewardCommand(UUID sessionId, RoomMatchContext context, int slotIndex, Integer cardIndex) {
}

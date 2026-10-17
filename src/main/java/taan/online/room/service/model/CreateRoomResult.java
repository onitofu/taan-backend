package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record CreateRoomResult(
    CreateRoomOutcome outcome,
    RoomSnapshot room,
    String reason,
    CompletionStage<Void> delivery
) {
    public CreateRoomResult(CreateRoomOutcome outcome, RoomSnapshot room, String reason) {
        this(outcome, room, reason, CompletableFuture.completedFuture(null));
    }
}

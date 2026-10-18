package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record EnterRoomResult(EnterRoomOutcome outcome, String reason, CompletionStage<Void> delivery) {
    public static EnterRoomResult rejected(EnterRoomOutcome outcome, String reason) {
        return new EnterRoomResult(outcome, reason, CompletableFuture.completedFuture(null));
    }
}

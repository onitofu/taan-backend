package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record SetRoomReadyResult(SetRoomReadyOutcome outcome, String reason, CompletionStage<Void> delivery) {
    public SetRoomReadyResult(SetRoomReadyOutcome outcome, String reason) {
        this(outcome, reason, CompletableFuture.completedFuture(null));
    }
}

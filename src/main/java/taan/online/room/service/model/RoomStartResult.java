package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.model.CaptainSelectionResult;

public record RoomStartResult(
    RoomStartOutcome outcome,
    String reason,
    CaptainSelectionResult captains,
    CompletionStage<Void> delivery
) {
    public RoomStartResult(RoomStartOutcome outcome, String reason) {
        this(outcome, reason, null, CompletableFuture.completedFuture(null));
    }
}

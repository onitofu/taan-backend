package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record SlotAvailabilityResult(SlotAvailabilityOutcome outcome, String reason, CompletionStage<Void> delivery) {
    public SlotAvailabilityResult(SlotAvailabilityOutcome outcome, String reason) {
        this(outcome, reason, CompletableFuture.completedFuture(null));
    }
}

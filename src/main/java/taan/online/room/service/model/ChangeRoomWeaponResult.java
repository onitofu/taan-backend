package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record ChangeRoomWeaponResult(ChangeRoomWeaponOutcome outcome, String reason, CompletionStage<Void> delivery) {
    public ChangeRoomWeaponResult(ChangeRoomWeaponOutcome outcome, String reason) {
        this(outcome, reason, CompletableFuture.completedFuture(null));
    }
}

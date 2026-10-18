package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.model.RoomSettings;

public record ChangeRoomSettingsResult(
    ChangeRoomSettingsOutcome outcome,
    String reason,
    RoomSettings settings,
    CompletionStage<Void> delivery
) {
    public ChangeRoomSettingsResult(ChangeRoomSettingsOutcome outcome, String reason, RoomSettings settings) {
        this(outcome, reason, settings, CompletableFuture.completedFuture(null));
    }
}

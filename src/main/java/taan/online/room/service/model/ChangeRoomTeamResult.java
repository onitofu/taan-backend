package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record ChangeRoomTeamResult(ChangeRoomTeamOutcome outcome, String reason, CompletionStage<Void> delivery) {
    public ChangeRoomTeamResult(ChangeRoomTeamOutcome outcome, String reason) {
        this(outcome, reason, CompletableFuture.completedFuture(null));
    }
}

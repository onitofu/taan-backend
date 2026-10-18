package taan.online.room.service.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record RoomMatchTerminationResult(RoomMatchTermination termination, CompletionStage<Void> delivery) {
    public static RoomMatchTerminationResult ignored() {
        return new RoomMatchTerminationResult(null, CompletableFuture.completedFuture(null));
    }
}

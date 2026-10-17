package taan.online.room.service.model;

import java.util.concurrent.CompletionStage;

public record EnterRoomResult(EnterRoomOutcome outcome, String reason, CompletionStage<Void> delivery) {
}

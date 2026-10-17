package taan.online.room.controller;

import java.util.concurrent.CompletionStage;

public record RoomReply(RoomReplyOutcome outcome, CompletionStage<Void> delivery) {
}

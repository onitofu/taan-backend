package taan.online.lobby.controller;

import java.util.concurrent.CompletionStage;

public record LobbyHandlingResult(LobbyHandlingOutcome outcome, CompletionStage<Void> delivery) {
}

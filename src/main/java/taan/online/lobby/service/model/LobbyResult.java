package taan.online.lobby.service.model;

import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelPresencePhase;

public record LobbyResult(ChannelPresencePhase phase, CompletionStage<Void> delivery) {
}

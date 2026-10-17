package taan.online.channel.service.model;

import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelPresencePhase;

public record ChannelSubscriptionResult(ChannelPresencePhase phase, int population, CompletionStage<Void> delivery) {
}

package taan.online.channel.service.model;

import taan.online.channel.model.ChannelSnapshot;

public record MoveToResult(MoveToOutcome outcome, int channelId, ChannelSnapshot channel) {
}

package taan.online.channel.service.model;

import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.model.ChannelPresencePhase;

public record ChannelMembershipSnapshot(int channelId, ChannelPresencePhase phase, ChannelParticipant participant) {
}

package taan.online.channel.service;

import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.model.ChannelPresencePhase;

final class ChannelMembership {
    final ChannelParticipant participant;
    final int channelId;
    ChannelPresencePhase phase = ChannelPresencePhase.MEMBER;
    ChannelPresenceOutput output;

    ChannelMembership(ChannelParticipant participant, int channelId) {
        this.participant = participant;
        this.channelId = channelId;
    }
}

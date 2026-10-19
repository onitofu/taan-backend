package taan.online.bootstrap;

import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.MatchAdmissionAccess;
import taan.online.match.service.model.MatchCommandContext;

public final class ChannelMatchAccess implements MatchAdmissionAccess {
    private final ChannelRegistry channels;

    public ChannelMatchAccess(ChannelRegistry channels) {
        this.channels = channels;
    }

    @Override
    public boolean current(MatchCommandContext context, MatchParticipant participant) {
        return channels.membership(context.sessionId()).filter(
            value ->
                value.channelId() == context.channelId() && value.phase() == ChannelPresencePhase.MEMBER
                    && value.participant().accountId().equals(participant.character().accountId())
                    && value.participant().characterId().equals(participant.character().id())
        ).isPresent();
    }
}

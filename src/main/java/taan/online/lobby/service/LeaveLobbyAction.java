package taan.online.lobby.service;

import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.channel.service.ChannelPresenceOutput;
import taan.online.channel.service.ChannelRegistry;
import taan.online.lobby.logging.LobbyLogEvent;
import taan.online.lobby.service.model.LobbyResult;
import taan.online.logging.EventLogger;

public final class LeaveLobbyAction {
    private static final EventLogger LOG = EventLogger.forClass(LeaveLobbyAction.class);

    private final ChannelRegistry channels;

    public LeaveLobbyAction(ChannelRegistry channels) {
        this.channels = channels;
    }

    public LobbyResult execute(UUID id, ChannelPresenceOutput output) {
        var result = channels.leave(id, output);
        LOG.log(LobbyLogEvent.LEFT, sessionId(id), outcome(result.phase().name()));
        return new LobbyResult(result.phase(), result.delivery());
    }
}

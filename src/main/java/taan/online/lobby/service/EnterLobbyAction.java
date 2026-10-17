package taan.online.lobby.service;

import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.population;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelPresenceOutput;
import taan.online.channel.service.ChannelRegistry;
import taan.online.lobby.logging.LobbyLogEvent;
import taan.online.lobby.service.model.LobbyResult;
import taan.online.logging.EventLogger;

public final class EnterLobbyAction {
    private static final EventLogger LOG = EventLogger.forClass(EnterLobbyAction.class);

    private final ChannelRegistry channels;

    public EnterLobbyAction(ChannelRegistry channels) {
        this.channels = channels;
    }

    public LobbyResult execute(UUID id, ChannelPresenceOutput output) {
        var result = channels.enter(id, output);
        if (result.phase() == ChannelPresencePhase.LOBBY) {
            LOG.log(
                LobbyLogEvent.ENTERED,
                sessionId(id),
                outcome(result.phase().name()),
                population(
                    result.population()
                )
            );
        }

        return new LobbyResult(result.phase(), result.delivery());
    }
}

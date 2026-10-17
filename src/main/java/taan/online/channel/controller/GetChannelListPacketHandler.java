package taan.online.channel.controller;

import java.util.Optional;
import java.util.UUID;
import taan.online.channel.protocol.codec.ChannelListRequestCodec;
import taan.online.channel.protocol.packet.PrGetChannelList;
import taan.online.channel.service.GetChannelListAction;
import taan.online.channel.service.SelectedCharacterAccess;

public final class GetChannelListPacketHandler {
    private final GetChannelListAction action;
    private final SelectedCharacterAccess sessions;

    public GetChannelListPacketHandler(GetChannelListAction action, SelectedCharacterAccess sessions) {
        this.action = action;
        this.sessions = sessions;
    }

    public Optional<PrGetChannelList> handle(UUID sessionId, byte[] root) {
        if (!sessions.hasSelectedCharacter(sessionId)) {
            return Optional.empty();
        }

        new ChannelListRequestCodec().decode(root);
        return action.execute(sessionId).filter(ignored -> sessions.hasSelectedCharacter(sessionId))
            .map(snapshot -> new ChannelCatalogMapper().map(snapshot));
    }
}

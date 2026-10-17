package taan.online.lobby.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.lobby.service.model.LobbyRoomListSnapshot;
import taan.online.room.service.RoomListingAccess;

public final class ListLobbyRoomsAction implements LobbyRoomListingAccess {
    private final ChannelRegistry channels;
    private final RoomListingAccess rooms;

    public ListLobbyRoomsAction(ChannelRegistry channels, RoomListingAccess rooms) {
        this.channels = channels;
        this.rooms = rooms;
    }

    @Override
    public Optional<LobbyRoomListSnapshot> snapshot(UUID id) {
        var context = channels.membership(id);

        if (context.isEmpty() || context.orElseThrow().phase() != ChannelPresencePhase.LOBBY) {
            return Optional.empty();
        }

        var member = context.orElseThrow();
        var result = new LobbyRoomListSnapshot(member.channelId(), rooms.forChannel(member.channelId()));
        return channels.membership(id).filter(member::equals).isEmpty() ? Optional.empty() : Optional.of(result);
    }
}

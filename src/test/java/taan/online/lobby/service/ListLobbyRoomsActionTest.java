package taan.online.lobby.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.RoomListingFixtures;
import taan.online.SelectionFixtures;
import taan.online.channel.service.ChannelTestAccess;
import taan.online.channel.service.model.MoveToCommand;

class ListLobbyRoomsActionTest {
    @Test
    void derivesTheChannelFromLiveMembershipAndRechecksItAfterReadingTheProjection() {
        var access = new ChannelTestAccess();
        var id = access.add(1, 1);
        var channels = SelectionFixtures.registry(access);
        var room = RoomListingFixtures.room(7, 1, "Room");
        var action = new ListLobbyRoomsAction(channels, channel -> channel == 1 ? List.of(room) : List.of());

        assertTrue(action.snapshot(UUID.randomUUID()).isEmpty());
        channels.move(id, new MoveToCommand(0, 1, 0, 929));

        assertTrue(action.snapshot(id).isEmpty());
        channels.enter(id, RoomListingFixtures.emptyView(new CollectingPacketOutput()));

        assertEquals(1, action.snapshot(id).orElseThrow().channelId());
        assertEquals(List.of(room), action.snapshot(id).orElseThrow().rooms());
        var changing = new ListLobbyRoomsAction(
            channels,
            channel -> {
                channels.move(id, new MoveToCommand(0, 2, 0, 929));
                return List.of(room);
            }
        );

        assertTrue(changing.snapshot(id).isEmpty());
        channels.enter(id, RoomListingFixtures.emptyView(new CollectingPacketOutput()));

        assertTrue(action.snapshot(id).orElseThrow().rooms().isEmpty());
    }
}

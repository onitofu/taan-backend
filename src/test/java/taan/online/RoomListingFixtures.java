package taan.online;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import taan.online.channel.service.ChannelRegistry;
import taan.online.lobby.service.ListLobbyRoomsAction;
import taan.online.lobby.service.model.LobbyRoomListSnapshot;
import taan.online.lobby.view.ProtocolLobbyView;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.PacketOutput;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTitle;
import taan.online.room.protocol.codec.GameSessionInfoCodec;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.service.RoomListingAccess;
import taan.online.room.service.model.RoomListing;

public final class RoomListingFixtures {
    private RoomListingFixtures() {
    }

    public static ProtocolLobbyView view(UUID id, ChannelRegistry channels, RoomListingAccess rooms, PacketOutput out) {
        return new ProtocolLobbyView(id, new ListLobbyRoomsAction(channels, rooms), out, 16384);
    }

    public static ProtocolLobbyView emptyView(PacketOutput out) {
        return new ProtocolLobbyView(
            UUID.randomUUID(),
            id -> Optional.of(new LobbyRoomListSnapshot(1, List.of())),
            out,
            16384
        );
    }

    public static byte[] create(String title) {
        var output = new BinaryWriter().unsignedInt(PqGameSessionCreate.TYPE_ID);
        new GameSessionInfoCodec().encode(
            output,
            new GameSessionInfo(65535, 5, new GameSessionDetails(0, title, 0, 0, 2, 0, 8, null, 1))
        );
        return output.bytes(new byte[21]).toByteArray();
    }

    public static RoomListing room(int id, int channel, String title) {
        return new RoomListing(
            new RoomId(id),
            channel,
            new RoomTitle(title),
            RoomState.WAITING,
            RoomMode.ITEM,
            RoomRule.BONUS,
            1,
            8,
            1
        );
    }
}

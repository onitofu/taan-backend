package taan.online.lobby.protocol.packet;

import java.util.List;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public record PcLobbyUpdateRoomList(List<GameSessionInfoPack> rooms) {
    public static final int TYPE_ID = 0x611208a6;

    public PcLobbyUpdateRoomList {
        rooms = List.copyOf(rooms);
    }
}

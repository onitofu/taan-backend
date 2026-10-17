package taan.online.lobby.protocol.packet;

import java.util.List;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public record PcLobbyInitRoomList(List<GameSessionInfoPack> rooms) {
    public static final int TYPE_ID = 0x500c07d7;

    public PcLobbyInitRoomList {
        rooms = List.copyOf(rooms);
    }
}

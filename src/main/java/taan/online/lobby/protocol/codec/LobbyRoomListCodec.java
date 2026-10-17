package taan.online.lobby.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateRoomList;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.codec.GameSessionInfoPackCodec;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public final class LobbyRoomListCodec {
    public byte[] encode(PcLobbyInitRoomList response) {
        return encode(PcLobbyInitRoomList.TYPE_ID, response.rooms());
    }

    public byte[] encode(PcLobbyUpdateRoomList response) {
        return encode(PcLobbyUpdateRoomList.TYPE_ID, response.rooms());
    }

    public PcLobbyInitRoomList initialize(byte[] root) {
        return new PcLobbyInitRoomList(decode(root, PcLobbyInitRoomList.TYPE_ID));
    }

    public PcLobbyUpdateRoomList update(byte[] root) {
        return new PcLobbyUpdateRoomList(decode(root, PcLobbyUpdateRoomList.TYPE_ID));
    }

    private byte[] encode(int type, List<GameSessionInfoPack> rooms) {
        var output = new BinaryWriter().unsignedInt(Integer.toUnsignedLong(type)).unsignedInt(rooms.size());
        rooms.forEach(room -> new GameSessionInfoPackCodec().encode(output, room));
        return output.toByteArray();
    }

    private List<GameSessionInfoPack> decode(byte[] root, int type) {
        var input = new BinaryReader(root, type);
        long count = input.unsignedInt();

        if (count > input.remaining() / 8) {
            throw new MalformedPacketException("INVALID_ROOM_LIST_COUNT");
        }

        var rooms = new ArrayList<GameSessionInfoPack>();

        for (long index = 0; index < count; index++) {
            rooms.add(new GameSessionInfoPackCodec().decode(input));
        }

        input.finish();
        return List.copyOf(rooms);
    }
}

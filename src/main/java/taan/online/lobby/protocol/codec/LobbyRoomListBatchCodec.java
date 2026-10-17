package taan.online.lobby.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateRoomList;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.codec.GameSessionInfoPackCodec;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public final class LobbyRoomListBatchCodec {
    public static final int MAX_PUBLIC_ENTRY_BYTES = 535;
    public static final int HEADER_BYTES = 8;

    private final int maxFrameBytes;

    public LobbyRoomListBatchCodec(int maxFrameBytes) {
        if (maxFrameBytes < HEADER_BYTES) {
            throw new IllegalArgumentException("Room list header exceeds frame limit");
        }

        this.maxFrameBytes = maxFrameBytes;
    }

    public List<byte[]> initialize(List<GameSessionInfoPack> rooms) {
        return encode(rooms, true);
    }

    public List<byte[]> update(List<GameSessionInfoPack> rooms) {
        return encode(rooms, false);
    }

    private List<byte[]> encode(List<GameSessionInfoPack> rooms, boolean initializing) {
        var result = new ArrayList<byte[]>();
        var entries = new ArrayList<byte[]>();
        int bytes = HEADER_BYTES;

        for (var room : rooms) {
            var output = new BinaryWriter();
            new GameSessionInfoPackCodec().encode(output, room);
            byte[] entry = output.toByteArray();

            if (entry.length > maxFrameBytes - HEADER_BYTES) {
                throw new IllegalArgumentException("Room list entry exceeds frame limit");
            }

            if (entry.length > maxFrameBytes - bytes) {
                result.add(packet(entries, initializing && result.isEmpty()));
                entries.clear();
                bytes = HEADER_BYTES;
            }

            entries.add(entry);
            bytes += entry.length;
        }

        if (!entries.isEmpty() || initializing) {
            result.add(packet(entries, initializing && result.isEmpty()));
        }

        return List.copyOf(result);
    }

    private byte[] packet(List<byte[]> entries, boolean initializing) {
        var output = new BinaryWriter().unsignedInt(
            initializing ? PcLobbyInitRoomList.TYPE_ID : PcLobbyUpdateRoomList.TYPE_ID
        ).unsignedInt(entries.size());
        entries.forEach(output::bytes);
        return output.toByteArray();
    }
}

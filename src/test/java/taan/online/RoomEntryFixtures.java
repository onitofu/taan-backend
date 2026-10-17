package taan.online;

import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.packet.PqGameSessionEnter;

public final class RoomEntryFixtures {
    public static byte[] enter(int roomId) {
        return enter(roomId, "");
    }

    public static byte[] enter(int roomId, String password) {
        return new BinaryWriter().unsignedInt(PqGameSessionEnter.TYPE_ID).unsignedShort(roomId)
            .wideString(password).bytes(new byte[21]).toByteArray();
    }
}

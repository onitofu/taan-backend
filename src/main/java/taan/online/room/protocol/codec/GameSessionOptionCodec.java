package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionOption;

public final class GameSessionOptionCodec {
    public GameSessionOption decode(BinaryReader input) {
        if (input.unsignedInt() != Integer.toUnsignedLong(GameSessionOption.TYPE_ID)) {
            throw new MalformedPacketException("UNEXPECTED_ROOM_OPTION_TYPE");
        }

        return new GameSessionOption(input.unsignedInt(), input.unsignedInt());
    }

    public void encode(BinaryWriter output, GameSessionOption option) {
        output.unsignedInt(GameSessionOption.TYPE_ID).unsignedInt(option.ownerCharacterId())
            .unsignedInt(option.ownerAccountId());
    }
}

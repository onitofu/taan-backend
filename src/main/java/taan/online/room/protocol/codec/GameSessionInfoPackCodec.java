package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public final class GameSessionInfoPackCodec {
    public GameSessionInfoPack decode(BinaryReader input) {
        if (input.unsignedInt() != Integer.toUnsignedLong(GameSessionInfoPack.TYPE_ID)) {
            throw new MalformedPacketException("UNEXPECTED_ROOM_PACK_TYPE");
        }

        int mask = input.unsignedShort();
        validate(mask);
        int roomId = input.unsignedShort();
        int state = (mask & 0x0001) == 0 ? 0 : input.unsignedByte();
        int roomGrade = (mask & 0x0200) == 0 ? 0 : input.unsignedByte();
        String title = (mask & 0x0002) == 0 ? "" : input.wideString();
        int gameKind = (mask & 0x0100) == 0 ? 0 : input.unsignedByte();
        int gameType = (mask & 0x0004) == 0 ? 0 : input.unsignedShort();
        int rule = (mask & 0x0080) == 0 ? 0 : input.unsignedByte();
        int playerCount = (mask & 0x0008) == 0 ? 0 : input.unsignedByte();
        int capacity = (mask & 0x0010) == 0 ? 0 : input.unsignedByte();
        int mapId = (mask & 0x0040) == 0 ? 0 : input.unsignedShort();
        String password = null;

        if ((mask & 0x0020) != 0 && input.unsignedByte() != 0) {
            password = input.wideString();
        }

        return new GameSessionInfoPack(
            mask,
            roomId,
            state,
            roomGrade,
            title,
            gameKind,
            gameType,
            rule,
            playerCount,
            capacity,
            mapId,
            password
        );
    }

    public void encode(BinaryWriter output, GameSessionInfoPack pack) {
        validate(pack.mask());
        output.unsignedInt(GameSessionInfoPack.TYPE_ID).unsignedShort(pack.mask()).unsignedShort(pack.roomId());

        if ((pack.mask() & 0x0001) != 0) {
            output.unsignedByte(pack.state());
        }

        if ((pack.mask() & 0x0200) != 0) {
            output.unsignedByte(pack.roomGrade());
        }

        if ((pack.mask() & 0x0002) != 0) {
            output.wideString(pack.title());
        }

        if ((pack.mask() & 0x0100) != 0) {
            output.unsignedByte(pack.gameKind());
        }

        if ((pack.mask() & 0x0004) != 0) {
            output.unsignedShort(pack.gameType());
        }

        if ((pack.mask() & 0x0080) != 0) {
            output.unsignedByte(pack.rule());
        }

        if ((pack.mask() & 0x0008) != 0) {
            output.unsignedByte(pack.playerCount());
        }

        if ((pack.mask() & 0x0010) != 0) {
            output.unsignedByte(pack.capacity());
        }

        if ((pack.mask() & 0x0040) != 0) {
            output.unsignedShort(pack.mapId());
        }

        if ((pack.mask() & 0x0020) != 0) {
            output.unsignedByte(pack.password() == null ? 0 : 1);

            if (pack.password() != null) {
                output.wideString(pack.password());
            }
        }
    }

    private void validate(int mask) {
        if ((mask & ~0x83ff) != 0) {
            throw new MalformedPacketException("UNSUPPORTED_ROOM_PACK_MASK");
        }
    }
}

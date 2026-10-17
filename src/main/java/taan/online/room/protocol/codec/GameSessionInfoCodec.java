package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;

public final class GameSessionInfoCodec {
    public GameSessionInfo decode(BinaryReader input) {
        if (input.unsignedInt() != Integer.toUnsignedLong(GameSessionInfo.TYPE_ID)) {
            throw new MalformedPacketException("UNEXPECTED_ROOM_INFO_TYPE");
        }

        int roomId = input.unsignedShort();
        int state = input.unsignedByte();

        if (state == 0) {
            return new GameSessionInfo(roomId, state, null);
        }

        int grade = input.unsignedByte();
        String title = input.wideString();
        int kind = input.unsignedByte();
        int type = input.unsignedShort();
        int rule = input.unsignedByte();
        int count = input.unsignedByte();
        int capacity = input.unsignedByte();
        String password = input.unsignedByte() == 0 ? null : input.wideString();
        var details = new GameSessionDetails(
            grade,
            title,
            kind,
            type,
            rule,
            count,
            capacity,
            password,
            input.unsignedShort()
        );
        return new GameSessionInfo(roomId, state, details);
    }

    public void encode(BinaryWriter output, GameSessionInfo info) {
        if ((info.state() == 0) != (info.details() == null)) {
            throw new MalformedPacketException("INCONSISTENT_ROOM_INFO_FIELDS");
        }

        output.unsignedInt(GameSessionInfo.TYPE_ID).unsignedShort(info.roomId()).unsignedByte(info.state());

        if (info.state() == 0) {
            return;
        }

        var details = info.details();
        output.unsignedByte(details.roomGrade()).wideString(details.title()).unsignedByte(details.gameKind())
            .unsignedShort(details.gameType()).unsignedByte(details.rule()).unsignedByte(details.playerCount())
            .unsignedByte(details.capacity()).unsignedByte(details.password() == null ? 0 : 1);

        if (details.password() != null) {
            output.wideString(details.password());
        }

        output.unsignedShort(details.mapId());
    }
}

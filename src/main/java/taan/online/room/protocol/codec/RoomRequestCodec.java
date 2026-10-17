package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionStartGame;

public final class RoomRequestCodec {
    public PqGameSessionStartGame start(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionStartGame.TYPE_ID);
        input.finish();
        return new PqGameSessionStartGame();
    }

    public PqGameSessionCreate create(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionCreate.TYPE_ID);
        var request = new PqGameSessionCreate(
            new GameSessionInfoCodec().decode(input),
            new PeerMetaCodec().decode(input)
        );
        input.finish();
        return request;
    }

    public PqGameSessionEnter enter(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionEnter.TYPE_ID);
        var request = new PqGameSessionEnter(
            input.unsignedShort(),
            input.wideString(),
            new PeerMetaCodec().decode(input)
        );
        input.finish();
        return request;
    }

    public PqGameSessionLeave leave(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionLeave.TYPE_ID);
        var request = new PqGameSessionLeave(input.unsignedShort(), input.unsignedByte());
        input.finish();
        return request;
    }
}

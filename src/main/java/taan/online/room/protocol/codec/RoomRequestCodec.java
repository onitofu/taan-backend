package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionStartGame;

public final class RoomRequestCodec {
    public PqGameSessionChangeTeam changeTeam(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionChangeTeam.TYPE_ID);
        var request = new PqGameSessionChangeTeam(input.unsignedByte());
        input.finish();
        return request;
    }

    public PqGameSessionOpenCloseSlot slotAvailability(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionOpenCloseSlot.TYPE_ID);
        var request = new PqGameSessionOpenCloseSlot(input.unsignedByte(), input.unsignedByte());
        input.finish();
        return request;
    }

    public PqGameSessionQuickJoin quickJoin(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionQuickJoin.TYPE_ID);
        var request = new PqGameSessionQuickJoin(input.unsignedShort(), new PeerMetaCodec().decode(input));
        input.finish();
        return request;
    }

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

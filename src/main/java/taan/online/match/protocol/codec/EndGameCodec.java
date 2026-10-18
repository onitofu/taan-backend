package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGameSessionEndGame;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class EndGameCodec {
    public byte[] encode(PcGameSessionEndGame message) {
        var output = new BinaryWriter().unsignedInt(PcGameSessionEndGame.TYPE_ID);
        new GameResultCodec().encode(output, message.result());
        return output.unsignedByte(message.packetFlag()).toByteArray();
    }

    public PcGameSessionEndGame decode(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionEndGame.TYPE_ID);
        var message = new PcGameSessionEndGame(new GameResultCodec().decode(input), input.unsignedByte());
        input.finish();
        return message;
    }
}

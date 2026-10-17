package taan.online.protocol.connection.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.connection.packet.PcNoticeMsg;

public final class NoticeMessageCodec {
    public byte[] encode(PcNoticeMsg message) {
        return new BinaryWriter().unsignedInt(PcNoticeMsg.TYPE_ID).wideString(message.message()).toByteArray();
    }

    public PcNoticeMsg decode(byte[] root) {
        var input = new BinaryReader(root, PcNoticeMsg.TYPE_ID);
        var message = new PcNoticeMsg(input.wideString());
        input.finish();
        return message;
    }
}

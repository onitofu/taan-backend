package taan.online.channel.protocol.codec;

import taan.online.channel.protocol.packet.PrChannelMoveTo;
import taan.online.protocol.BinaryWriter;

public final class MoveToResponseCodec {
    public byte[] encode(PrChannelMoveTo reply) {
        var output = new BinaryWriter().unsignedInt(PrChannelMoveTo.TYPE_ID).unsignedByte(reply.result())
            .unsignedShort(reply.channelId());
        var channel = new ChannelInfoCodec();
        channel.endpoint(output, reply.port());
        output.unsignedInt(0).unsignedByte(reply.info() == null ? 0 : 1);

        if (reply.info() != null) {
            channel.write(output, reply.info());
        }

        return output.toByteArray();
    }
}

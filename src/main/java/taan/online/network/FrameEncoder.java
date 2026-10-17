package taan.online.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class FrameEncoder extends MessageToByteEncoder<byte[]> {
    @Override
    protected void encode(ChannelHandlerContext context, byte[] root, ByteBuf output) {
        output.writeIntLE(root.length);
        output.writeBytes(root);
    }
}

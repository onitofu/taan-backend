package taan.online.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import taan.online.protocol.MalformedPacketException;

public final class FrameDecoder extends ByteToMessageDecoder {
    private final int maxRootBytes;
    private final int assemblyTimeoutSeconds;
    private ScheduledFuture<?> deadline;
    private boolean timedOut;

    public FrameDecoder(int maxRootBytes, int assemblyTimeoutSeconds) {
        this.maxRootBytes = maxRootBytes;
        this.assemblyTimeoutSeconds = assemblyTimeoutSeconds;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        if (timedOut) {
            return;
        }

        if (deadline != null && deadline.getDelay(TimeUnit.NANOSECONDS) <= 0) {
            timeout(context);
            return;
        }

        if (!input.isReadable()) {
            return;
        }

        if (deadline == null) {
            deadline = context.executor().schedule(() -> timeout(context), assemblyTimeoutSeconds, TimeUnit.SECONDS);
        }

        if (input.readableBytes() < 4) {
            return;
        }

        long length = input.getUnsignedIntLE(input.readerIndex());

        if (length < 4 || length > maxRootBytes) {
            cancelDeadline();
            throw new MalformedPacketException("FRAME_SIZE");
        }

        if (input.readableBytes() - 4 < length) {
            return;
        }

        cancelDeadline();
        input.skipBytes(4);
        byte[] root = new byte[(int) length];
        input.readBytes(root);
        output.add(root);
    }

    @Override
    protected void decodeLast(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        try {
            decode(context, input, output);

            if (!timedOut && input.isReadable()) {
                throw new MalformedPacketException("TRUNCATED_FRAME");
            }
        } finally {
            cancelDeadline();
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext context) {
        cancelDeadline();
    }

    private void timeout(ChannelHandlerContext context) {
        timedOut = true;
        cancelDeadline();

        try {
            context.fireUserEventTriggered(FrameAssemblyTimeoutEvent.INSTANCE);
        } finally {
            context.close();
        }
    }

    private void cancelDeadline() {
        if (deadline != null) {
            deadline.cancel(false);
            deadline = null;
        }
    }
}

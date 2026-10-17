package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import java.util.ArrayDeque;

final class QueuedOutboundWrites extends ChannelOutboundHandlerAdapter {
    private final ArrayDeque<PendingOutboundWrite> pending = new ArrayDeque<>();

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        pending.addLast(new PendingOutboundWrite(context, message, promise));
    }

    void send() {
        var write = pending.removeFirst();
        write.context().writeAndFlush(write.message(), write.promise());
    }

    void fail() {
        pending.removeFirst().promise().setFailure(new IOException("Private transport message"));
    }

    void succeed() {
        pending.removeFirst().promise().setSuccess();
    }
}

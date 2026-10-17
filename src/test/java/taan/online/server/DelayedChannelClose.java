package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

final class DelayedChannelClose extends ChannelOutboundHandlerAdapter {
    private ChannelHandlerContext context;
    private ChannelPromise promise;
    int calls;

    @Override
    public void close(ChannelHandlerContext context, ChannelPromise promise) {
        this.context = context;
        this.promise = promise;
        calls++;
    }

    void finish() {
        context.close(promise);
    }
}

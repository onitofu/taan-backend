package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.IOException;

final class DelayedOutboundWrite extends ChannelOutboundHandlerAdapter {
    private ChannelHandlerContext context;
    private ChannelPromise promise;
    private Object message;

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        this.context = context;
        this.promise = promise;
        this.message = message;
    }

    void send() {
        context.writeAndFlush(message, promise);
    }

    void fail() {
        promise.setFailure(new IOException("Write failed"));
    }

    void succeed() {
        promise.setSuccess();
    }
}

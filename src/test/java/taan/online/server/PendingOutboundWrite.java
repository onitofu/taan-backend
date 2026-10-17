package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

record PendingOutboundWrite(ChannelHandlerContext context, Object message, ChannelPromise promise) {
}

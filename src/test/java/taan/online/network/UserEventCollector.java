package taan.online.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.List;

final class UserEventCollector extends ChannelInboundHandlerAdapter {
    private final List<Object> events;

    UserEventCollector(List<Object> events) {
        this.events = events;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) {
        events.add(event);
    }
}

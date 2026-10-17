package taan.online.network;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import taan.online.logging.WarningLogLimiter;

final class TcpChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ChannelGroup clients;
    private final TcpServerSettings settings;
    private final Semaphore permits;
    private final WarningLogLimiter warnings;
    private final Supplier<ChannelHandler> handlers;

    TcpChannelInitializer(
        ChannelGroup clients,
        TcpServerSettings settings,
        Semaphore permits,
        WarningLogLimiter warnings,
        Supplier<ChannelHandler> handlers
    ) {
        this.clients = clients;
        this.settings = settings;
        this.permits = permits;
        this.warnings = warnings;
        this.handlers = handlers;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        clients.add(channel);
        channel.pipeline().addLast(
            new ConnectionLimitHandler(permits, warnings),
            new IdleStateHandler(settings.idleTimeoutSeconds(), 0, 0),
            new WriteTimeoutHandler(settings.writeTimeoutSeconds()),
            new FrameDecoder(settings.maxFrameBytes(), settings.frameAssemblyTimeoutSeconds()),
            new FrameEncoder(),
            handlers.get()
        );
    }
}

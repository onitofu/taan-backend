package taan.online.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import taan.online.logging.WarningLogLimiter;

public final class TcpServer implements AutoCloseable {
    private final MultiThreadIoEventLoopGroup acceptor = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final MultiThreadIoEventLoopGroup readers = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final DefaultChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final TcpServerSettings settings;
    private final Semaphore permits;
    private final WarningLogLimiter warnings;
    private final Supplier<ChannelHandler> handlers;
    private Channel listener;

    public TcpServer(TcpServerSettings settings, WarningLogLimiter warnings, Supplier<ChannelHandler> handlers) {
        this.settings = settings;
        this.warnings = warnings;
        this.handlers = handlers;
        permits = new Semaphore(settings.maxConnections());
    }

    public void start() throws InterruptedException {
        listener = new ServerBootstrap().group(acceptor, readers).channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childHandler(new TcpChannelInitializer(clients, settings, permits, warnings, handlers))
            .bind(settings.bindAddress(), settings.port()).sync().channel();
    }

    public int port() {
        return ((InetSocketAddress) listener.localAddress()).getPort();
    }

    public void stopAccepting() {
        if (listener != null) {
            listener.close().awaitUninterruptibly(1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        stopAccepting();
        clients.close().awaitUninterruptibly(remaining(deadline), TimeUnit.NANOSECONDS);

        var acceptorStopped = acceptor.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        var readersStopped = readers.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        acceptorStopped.awaitUninterruptibly(remaining(deadline), TimeUnit.NANOSECONDS);
        readersStopped.awaitUninterruptibly(remaining(deadline), TimeUnit.NANOSECONDS);
    }

    private long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }
}

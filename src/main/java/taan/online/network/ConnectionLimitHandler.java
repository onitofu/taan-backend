package taan.online.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.Semaphore;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;

public final class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {
    private final Semaphore permits;
    private final WarningLogLimiter warnings;
    private boolean acquired;

    public ConnectionLimitHandler(Semaphore permits, WarningLogLimiter warnings) {
        this.permits = permits;
        this.warnings = warnings;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        acquired = permits.tryAcquire();

        if (!acquired) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "CONNECTION_LIMIT");
            context.close();
            return;
        }

        super.channelActive(context);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        if (acquired) {
            acquired = false;
            permits.release();
            super.channelInactive(context);
        }
    }
}

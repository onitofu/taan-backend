package taan.online.server;

import static taan.online.logging.LogField.connectionId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.operationId;
import static taan.online.logging.LogField.packetType;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.remoteAddress;
import static taan.online.logging.LogField.rootBytes;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.WriteTimeoutException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.logging.AccountLogEvent;
import taan.online.account.protocol.codec.CheckAccountRequestCodec;
import taan.online.account.protocol.codec.CheckAccountResponseCodec;
import taan.online.account.protocol.codec.CreateAccountRequestCodec;
import taan.online.account.protocol.codec.CreateAccountResponseCodec;
import taan.online.account.protocol.packet.AccountRequest;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.account.protocol.packet.PrCheckAccount;
import taan.online.account.protocol.packet.PrCreateAccount;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.ConnectionId;
import taan.online.network.FrameAssemblyTimeoutEvent;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.FirstAcceptCodec;
import taan.online.protocol.connection.PcFirstAccept;
import taan.online.server.logging.ConnectionLogEvent;

public final class ConnectionContext extends SimpleChannelInboundHandler<byte[]> {
    private static final EventLogger LOG = EventLogger.forClass(ConnectionContext.class);
    private static final int FIRST_PACKET_TYPE_ID = 0x230e0521;

    private final ConnectionId id = ConnectionId.create();
    private final ArrayDeque<PendingAccountRequest> pending = new ArrayDeque<>();
    private final ThreadPoolExecutor executor;
    private final CheckAccountPacketHandler check;
    private final CreateAccountPacketHandler create;
    private final WarningLogLimiter warnings;
    private final int maxPending;
    private final int timeoutSeconds;

    private ConnectionPhase phase = ConnectionPhase.OPEN;
    private String closeReason = "PEER_CLOSED";
    private ScheduledFuture<?> deadline;
    private RequestTask activeTask;

    public ConnectionContext(
        ThreadPoolExecutor executor,
        CheckAccountPacketHandler check,
        CreateAccountPacketHandler create,
        WarningLogLimiter warnings,
        int maxPending,
        int timeoutSeconds
    ) {
        this.executor = executor;
        this.check = check;
        this.create = create;
        this.warnings = warnings;
        this.maxPending = maxPending;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        LOG.log(ConnectionLogEvent.OPENED, connectionId(id.value()), remoteAddress(context.channel().remoteAddress()));

        var greeting = new PcFirstAccept(0xa815b62300000000L, 0, 0);
        context.writeAndFlush(new FirstAcceptCodec().encode(greeting))
            .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, byte[] root) {
        if (phase == ConnectionPhase.CLOSING || phase == ConnectionPhase.CLOSED) {
            return;
        }

        int type = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).getInt();

        if (type == FIRST_PACKET_TYPE_ID) {
            close(context, "LOGIN_NOT_IMPLEMENTED");
            return;
        }

        LOG.log(ConnectionLogEvent.PACKET_RECEIVED, connectionId(id.value()), packetType(type), rootBytes(root.length));

        AccountRequest request = switch (type) {
            case PqCheckAccount.TYPE_ID -> new CheckAccountRequestCodec().decode(root);
            case PqCreateAccount.TYPE_ID -> new CreateAccountRequestCodec().decode(root);
            default -> throw new MalformedPacketException("UNSUPPORTED_PACKET");
        };

        if (pending.size() >= maxPending) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "CONNECTION_QUEUE_FULL", id.value().toString());
            close(context, "CONNECTION_QUEUE_FULL");
            return;
        }

        pending.addLast(new PendingAccountRequest(UUID.randomUUID(), request));
        next(context);
    }

    private void next(ChannelHandlerContext context) {
        if (phase != ConnectionPhase.OPEN || pending.isEmpty()) {
            return;
        }

        phase = ConnectionPhase.EXECUTING;
        PendingAccountRequest request = pending.getFirst();
        deadline = context.executor().schedule(
            () -> close(context, "REQUEST_TIMEOUT"),
            timeoutSeconds,
            TimeUnit.SECONDS
        );
        activeTask = new RequestTask(request.operationId(), () -> execute(context, request));

        try {
            executor.execute(activeTask);
        } catch (RejectedExecutionException failure) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "WORK_QUEUE_FULL", id.value().toString());
            close(context, "WORK_QUEUE_FULL");
        }
    }

    private void execute(ChannelHandlerContext context, PendingAccountRequest pendingRequest) {
        byte[] response;
        boolean completed;
        long started = System.nanoTime();

        try (var scope = new LogContext(id.value().toString(), pendingRequest.operationId().toString())) {
            try {
                if (pendingRequest.request() instanceof PqCheckAccount request) {
                    response = new CheckAccountResponseCodec().encode(check.handle(request));
                    completed = false;
                } else {
                    var reply = create.handle((PqCreateAccount) pendingRequest.request());
                    response = new CreateAccountResponseCodec().encode(reply);
                    completed = reply.status() == 0;
                }
            } catch (RuntimeException failure) {
                boolean registration = pendingRequest.request() instanceof PqCreateAccount;
                AccountLogEvent event = registration
                    ? AccountLogEvent.REGISTRATION_FAILED
                    : AccountLogEvent.CHECK_FAILED;

                LOG.failure(event, failure, durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));

                int selector = pendingRequest.request().selector();
                response = registration
                    ? new CreateAccountResponseCodec().encode(new PrCreateAccount(255, selector))
                    : new CheckAccountResponseCodec().encode(new PrCheckAccount(255, selector));
                completed = false;
            }
        }

        byte[] reply = response;
        boolean terminal = completed;

        try {
            context.executor().execute(() -> complete(context, reply, terminal));
        } catch (RejectedExecutionException ignored) {
            context.channel().close();
        }
    }

    private void complete(ChannelHandlerContext context, byte[] response, boolean terminal) {
        if (phase != ConnectionPhase.EXECUTING) {
            return;
        }

        deadline.cancel(false);
        activeTask = null;

        if (terminal) {
            phase = ConnectionPhase.CLOSING;
            closeReason = "REGISTRATION_COMPLETED";
            pending.clear();
        }

        ConnectionPhase awaitingWrite = phase;
        context.writeAndFlush(response).addListener(
            result -> {
                if (phase != awaitingWrite) {
                    return;
                }

                if (!result.isSuccess()) {
                    close(context, "WRITE_FAILED");
                } else if (terminal) {
                    context.close();
                } else {
                    pending.removeFirst();
                    phase = ConnectionPhase.OPEN;
                    next(context);
                }
            }
        );
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event == FrameAssemblyTimeoutEvent.INSTANCE) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "FRAME_ASSEMBLY_TIMEOUT", id.value().toString());
            close(context, "FRAME_ASSEMBLY_TIMEOUT");
        } else if (event instanceof IdleStateEvent) {
            close(context, "IDLE_TIMEOUT");
        } else {
            super.userEventTriggered(context, event);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
        if (phase == ConnectionPhase.CLOSING || phase == ConnectionPhase.CLOSED) {
            return;
        }

        if (failure instanceof MalformedPacketException || failure instanceof DecoderException) {
            warnings.warn(WarningEvent.FRAME_REJECTED, "INVALID_FRAME_OR_PACKET", id.value().toString());
            close(context, "PROTOCOL_ERROR");
        } else if (failure instanceof IOException || failure instanceof WriteTimeoutException) {
            close(context, "TRANSPORT_CLOSED");
        } else {
            LOG.failure(ConnectionLogEvent.FAILED, failure, connectionId(id.value()));
            close(context, "INTERNAL_ERROR");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        phase = ConnectionPhase.CLOSED;
        cancelWork(closeReason);
        pending.clear();

        if (deadline != null) {
            deadline.cancel(false);
        }

        LOG.log(ConnectionLogEvent.CLOSED, connectionId(id.value()), reason(closeReason));
    }

    private void close(ChannelHandlerContext context, String reason) {
        if (phase == ConnectionPhase.CLOSED) {
            return;
        }

        phase = ConnectionPhase.CLOSING;
        closeReason = reason;
        cancelWork(reason);
        pending.clear();

        if (deadline != null) {
            deadline.cancel(false);
        }

        context.close();
    }

    private void cancelWork(String reason) {
        RequestTask task = activeTask;
        activeTask = null;

        if (task != null && task.cancel()) {
            executor.remove(task);
            LOG.log(
                AccountLogEvent.REQUEST_CANCELLED,
                connectionId(id.value()),
                operationId(task.operationId()),
                reason(reason)
            );
        }
    }
}

package taan.online.server;

import static taan.online.logging.LogField.connectionId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.operationId;
import static taan.online.logging.LogField.packetType;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.remoteAddress;
import static taan.online.logging.LogField.rootBytes;
import static taan.online.logging.LogField.sessionId;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.WriteTimeoutException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
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
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.account.protocol.packet.PrCheckAccount;
import taan.online.account.protocol.packet.PrCreateAccount;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.LogEvent;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.ConnectionId;
import taan.online.network.FrameAssemblyTimeoutEvent;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.codec.FirstAcceptCodec;
import taan.online.protocol.connection.codec.FirstPacketRequestCodec;
import taan.online.protocol.connection.codec.FirstPacketResponseCodec;
import taan.online.protocol.connection.codec.KeepAliveCodec;
import taan.online.protocol.connection.packet.PcFirstAccept;
import taan.online.protocol.connection.packet.PqFirstPacket;
import taan.online.protocol.connection.packet.PqKeepAlive;
import taan.online.server.logging.ConnectionLogEvent;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.logging.SessionLogEvent;
import taan.online.session.protocol.codec.UserLoginResponseCodec;
import taan.online.session.protocol.packet.PqUserLogin;
import taan.online.session.protocol.packet.PrUserLogin;

public final class ConnectionContext extends SimpleChannelInboundHandler<byte[]> {
    private static final EventLogger LOG = EventLogger.forClass(ConnectionContext.class);

    private final ConnectionId id = ConnectionId.create();
    private final ArrayDeque<PendingRequest> pending = new ArrayDeque<>();
    private final ProtocolStateMachine protocol = new ProtocolStateMachine();
    private final ThreadPoolExecutor executor;
    private final CheckAccountPacketHandler check;
    private final CreateAccountPacketHandler create;
    private final UserLoginPacketHandler login;
    private final CharacterRequestDispatcher characters;
    private final Clock clock;
    private final int idleTimeoutSeconds;
    private final WarningLogLimiter warnings;
    private final int maxPending;
    private final int timeoutSeconds;

    private ConnectionPhase phase = ConnectionPhase.OPEN;
    private UUID sessionId;
    private ScheduledFuture<?> idleDeadline;
    private String closeReason = "PEER_CLOSED";
    private ScheduledFuture<?> deadline;
    private RequestTask activeTask;

    public ConnectionContext(
        ThreadPoolExecutor executor,
        CheckAccountPacketHandler check,
        CreateAccountPacketHandler create,
        WarningLogLimiter warnings,
        int maxPending,
        int timeoutSeconds,
        UserLoginPacketHandler login,
        Clock clock,
        int idleTimeoutSeconds,
        ConfirmNamePacketHandler confirmName,
        CreateCharacterPacketHandler createCharacter
    ) {
        this.executor = executor;
        this.check = check;
        this.create = create;
        this.warnings = warnings;
        this.maxPending = maxPending;
        this.timeoutSeconds = timeoutSeconds;
        this.login = login;
        this.clock = clock;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        characters = new CharacterRequestDispatcher(confirmName, createCharacter);
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        LOG.log(ConnectionLogEvent.OPENED, connectionId(id.value()), remoteAddress(context.channel().remoteAddress()));

        sessionId = login.open(id.value());
        context.channel().closeFuture().addListener(ignored -> cleanup());
        activity(context);

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

        LOG.log(ConnectionLogEvent.PACKET_RECEIVED, connectionId(id.value()), packetType(type), rootBytes(root.length));

        switch (type) {
            case PqCheckAccount.TYPE_ID, PqCreateAccount.TYPE_ID,
                PqFirstPacket.TYPE_ID, PqUserLogin.TYPE_ID, PqKeepAlive.TYPE_ID,
                PqConfirmName.TYPE_ID, PqNewCharacter.TYPE_ID -> { }
            default -> throw new MalformedPacketException("UNSUPPORTED_PACKET");
        }

        if (pending.size() >= maxPending) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "CONNECTION_QUEUE_FULL", id.value().toString());
            close(context, "CONNECTION_QUEUE_FULL");
            return;
        }

        pending.addLast(new PendingRequest(UUID.randomUUID(), type, root));
        next(context);
    }

    private void next(ChannelHandlerContext context) {
        while (phase == ConnectionPhase.OPEN && !pending.isEmpty()) {
            PendingRequest request = pending.getFirst();
            ProtocolDecision decision = protocol.on(requestEvent(request.type()));

            if (decision == ProtocolDecision.REJECT) {
                exceptionCaught(context, new MalformedPacketException("INVALID_PROTOCOL_STATE"));
                return;
            }

            if (decision == ProtocolDecision.IGNORE) {
                LOG.log(
                    SessionLogEvent.LOGIN_IGNORED,
                    connectionId(id.value()),
                    sessionId(sessionId),
                    operationId(request.operationId()),
                    reason("SESSION_NOT_ANONYMOUS")
                );
                pending.removeFirst();
                continue;
            }

            phase = ConnectionPhase.EXECUTING;
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

            return;
        }
    }

    private ProtocolEvent requestEvent(int type) {
        return switch (type) {
            case PqCheckAccount.TYPE_ID -> ProtocolEvent.CHECK_ACCOUNT;
            case PqCreateAccount.TYPE_ID -> ProtocolEvent.CREATE_ACCOUNT;
            case PqFirstPacket.TYPE_ID -> ProtocolEvent.FIRST_PACKET;
            case PqUserLogin.TYPE_ID -> ProtocolEvent.USER_LOGIN;
            case PqConfirmName.TYPE_ID -> ProtocolEvent.CONFIRM_NAME;
            case PqNewCharacter.TYPE_ID -> ProtocolEvent.NEW_CHARACTER;
            default -> ProtocolEvent.KEEP_ALIVE;
        };
    }

    private void execute(ChannelHandlerContext context, PendingRequest request) {
        RequestCompletion completion;
        long started = System.nanoTime();

        try (var scope = new LogContext(
            id.value().toString(),
            request.operationId().toString(),
            sessionId.toString()
        )) {
            try {
                completion = dispatch(request);
            } catch (MalformedPacketException failure) {
                onEventLoop(context, () -> exceptionCaught(context, failure));
                return;
            } catch (RuntimeException failure) {
                if (CharacterRequestDispatcher.accepts(request.type())) {
                    LOG.failure(
                        CharacterRequestDispatcher.failureEvent(request.type()),
                        failure,
                        durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    );
                    onEventLoop(context, () -> close(context, "CHARACTER_OPERATION_FAILED"));
                    return;
                }

                if (request.type() == PqFirstPacket.TYPE_ID) {
                    onEventLoop(context, () -> exceptionCaught(context, failure));
                    return;
                }

                LogEvent event = switch (request.type()) {
                    case PqCreateAccount.TYPE_ID -> AccountLogEvent.REGISTRATION_FAILED;
                    case PqUserLogin.TYPE_ID -> SessionLogEvent.LOGIN_FAILED;
                    default -> AccountLogEvent.CHECK_FAILED;
                };
                LOG.failure(event, failure, durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
                completion = failedReply(request);
            }
        }

        RequestCompletion result = completion;
        onEventLoop(context, () -> complete(context, result));
    }

    private RequestCompletion dispatch(PendingRequest request) {
        byte[] root = request.root();

        return switch (request.type()) {
            case PqCheckAccount.TYPE_ID -> new RequestCompletion(
                ProtocolEvent.ACCOUNT_CHECK_COMPLETED,
                new CheckAccountResponseCodec().encode(check.handle(new CheckAccountRequestCodec().decode(root)))
            );
            case PqCreateAccount.TYPE_ID -> {
                var reply = create.handle(new CreateAccountRequestCodec().decode(root));
                var event = reply.status() == 0
                    ? ProtocolEvent.REGISTRATION_CREATED : ProtocolEvent.REGISTRATION_REJECTED;
                yield new RequestCompletion(event, new CreateAccountResponseCodec().encode(reply));
            }
            case PqFirstPacket.TYPE_ID -> {
                new FirstPacketRequestCodec().decode(root);
                yield new RequestCompletion(
                    ProtocolEvent.HANDSHAKE_COMPLETED,
                    new FirstPacketResponseCodec().encode(clock)
                );
            }
            case PqUserLogin.TYPE_ID -> login.handle(root).map(
                reply -> new RequestCompletion(
                    reply.success() ? ProtocolEvent.LOGIN_SUCCEEDED : ProtocolEvent.LOGIN_REJECTED,
                    new UserLoginResponseCodec().encode(reply)
                )
            ).orElseGet(() -> new RequestCompletion(ProtocolEvent.LOGIN_IGNORED, null));
            case PqConfirmName.TYPE_ID, PqNewCharacter.TYPE_ID -> characters.dispatch(request.type(), sessionId, root);
            default -> {
                new KeepAliveCodec().decode(root);
                yield new RequestCompletion(ProtocolEvent.KEEP_ALIVE_ACCEPTED, null);
            }
        };
    }

    private RequestCompletion failedReply(PendingRequest request) {
        return switch (request.type()) {
            case PqCreateAccount.TYPE_ID -> new RequestCompletion(
                ProtocolEvent.REGISTRATION_REJECTED,
                new CreateAccountResponseCodec().encode(
                    new PrCreateAccount(255, new CreateAccountRequestCodec().decode(request.root()).selector())
                )
            );
            case PqCheckAccount.TYPE_ID -> new RequestCompletion(
                ProtocolEvent.ACCOUNT_CHECK_COMPLETED,
                new CheckAccountResponseCodec().encode(
                    new PrCheckAccount(255, new CheckAccountRequestCodec().decode(request.root()).selector())
                )
            );
            default -> new RequestCompletion(
                ProtocolEvent.LOGIN_REJECTED,
                new UserLoginResponseCodec().encode(new PrUserLogin(false, 0, 0, 0))
            );
        };
    }

    private void onEventLoop(ChannelHandlerContext context, Runnable callback) {
        try {
            context.executor().execute(callback);
        } catch (RejectedExecutionException ignored) {
            context.channel().close();
        }
    }

    private void complete(ChannelHandlerContext context, RequestCompletion completion) {
        if (phase != ConnectionPhase.EXECUTING) {
            return;
        }

        ProtocolDecision decision;

        try {
            decision = protocol.on(completion.event());
        } catch (IllegalStateException failure) {
            exceptionCaught(context, failure);
            return;
        }

        deadline.cancel(false);
        activeTask = null;
        byte[] response = completion.response();
        boolean terminal = decision == ProtocolDecision.CLOSE_AFTER_REPLY;

        if (decision != ProtocolDecision.IGNORE) {
            activity(context);
        }

        if (terminal) {
            phase = ConnectionPhase.CLOSING;
            closeReason = completion.event() == ProtocolEvent.LOGIN_REJECTED
                ? "LOGIN_REJECTED" : "REGISTRATION_COMPLETED";
            login.close(closeReason);
            pending.clear();
        }

        if (response == null) {
            pending.removeFirst();
            phase = ConnectionPhase.OPEN;
            next(context);
            return;
        }

        boolean closeAfterWrite = terminal;
        ConnectionPhase awaitingWrite = phase;
        context.writeAndFlush(response).addListener(
            result -> {
                if (phase != awaitingWrite) {
                    return;
                }

                if (!result.isSuccess()) {
                    close(context, "WRITE_FAILED");
                } else if (closeAfterWrite) {
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
        cleanup();

        LOG.log(ConnectionLogEvent.CLOSED, connectionId(id.value()), reason(closeReason));
    }

    private void close(ChannelHandlerContext context, String reason) {
        if (phase == ConnectionPhase.CLOSED) {
            return;
        }

        phase = ConnectionPhase.CLOSING;
        protocol.on(ProtocolEvent.CONNECTION_CLOSED);
        closeReason = reason;
        login.close(reason);
        cancelWork(reason);
        pending.clear();

        if (deadline != null) {
            deadline.cancel(false);
        }

        context.close();
    }

    private void activity(ChannelHandlerContext context) {
        if (idleDeadline != null) {
            idleDeadline.cancel(false);
        }

        idleDeadline = context.executor().schedule(
            () -> close(context, "IDLE_TIMEOUT"),
            idleTimeoutSeconds,
            TimeUnit.SECONDS
        );
    }

    private void cleanup() {
        phase = ConnectionPhase.CLOSED;
        protocol.on(ProtocolEvent.CONNECTION_CLOSED);
        login.close(closeReason);
        cancelWork(closeReason);
        pending.clear();

        if (deadline != null) {
            deadline.cancel(false);
        }

        if (idleDeadline != null) {
            idleDeadline.cancel(false);
        }
    }

    private void cancelWork(String reason) {
        RequestTask task = activeTask;
        activeTask = null;

        if (task != null && task.cancel()) {
            executor.remove(task);
            LogEvent event = switch (pending.peekFirst().type()) {
                case PqUserLogin.TYPE_ID -> SessionLogEvent.LOGIN_CANCELLED;
                case PqConfirmName.TYPE_ID, PqNewCharacter.TYPE_ID -> CharacterLogEvent.REQUEST_CANCELLED;
                default -> AccountLogEvent.REQUEST_CANCELLED;
            };
            LOG.log(
                event,
                connectionId(id.value()),
                operationId(task.operationId()),
                reason(reason)
            );
        }
    }
}

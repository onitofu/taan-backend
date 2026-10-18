package taan.online.server;

import static taan.online.logging.LogField.connectionId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.operationId;
import static taan.online.logging.LogField.packetType;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.remoteAddress;
import static taan.online.logging.LogField.rootBytes;
import static taan.online.logging.LogField.sessionId;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.WriteTimeoutException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.logging.AccountLogEvent;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.channel.protocol.packet.PqChannelMoveTo;
import taan.online.channel.protocol.packet.PqGetChannelList;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.controller.DeleteCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqDelCharacter;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.inventory.logging.InventoryLogEvent;
import taan.online.inventory.protocol.packet.PqMoveInventoryItem;
import taan.online.lobby.protocol.packet.PqLobbyEnter;
import taan.online.lobby.protocol.packet.PqLobbyLeave;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.LogEvent;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.ConnectionId;
import taan.online.network.FrameAssemblyTimeoutEvent;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.codec.FirstAcceptCodec;
import taan.online.protocol.connection.packet.PcFirstAccept;
import taan.online.protocol.connection.packet.PqFirstPacket;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionStartGame;
import taan.online.server.logging.ConnectionLogEvent;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.logging.SessionLogEvent;
import taan.online.session.protocol.packet.PcLogging;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.protocol.packet.PqUserLogin;

public final class ConnectionContext extends SimpleChannelInboundHandler<byte[]> {
    private static final EventLogger LOG = EventLogger.forClass(ConnectionContext.class);

    private final ConnectionId id = ConnectionId.create();
    private final ArrayDeque<PendingRequest> pending = new ArrayDeque<>();
    private final ProtocolStateMachine protocol = new ProtocolStateMachine();
    private final ThreadPoolExecutor executor;
    private final AccountRequestDispatcher accounts;
    private final UserLoginPacketHandler login;
    private final ConnectionRequestDispatcher requests;
    private final RoomRequestDispatcher rooms;
    private final LobbyRequestDispatcher lobby;
    private final int outputBatches;
    private final int outputBytes;
    private final int maxFrame;
    private ConnectionPacketOutput output;
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
        CreateCharacterPacketHandler createCharacter,
        DeleteCharacterPacketHandler deleteCharacter,
        SelectedCharacterRequestDispatcher selected,
        LobbyRequestDispatcher lobby,
        InventoryRequestDispatcher inventories,
        RoomRequestDispatcher rooms,
        int outputBatches,
        int outputBytes,
        int maxFrame
    ) {
        this.executor = executor;
        this.rooms = rooms;
        accounts = new AccountRequestDispatcher(check, create, login, clock);
        this.lobby = lobby;
        this.outputBatches = outputBatches;
        this.outputBytes = outputBytes;
        this.maxFrame = maxFrame;
        this.warnings = warnings;
        this.maxPending = maxPending;
        this.timeoutSeconds = timeoutSeconds;
        this.login = login;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        requests = new ConnectionRequestDispatcher(
            accounts,
            new CharacterRequestDispatcher(confirmName, createCharacter, deleteCharacter),
            selected,
            lobby,
            inventories,
            rooms
        );
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        LOG.log(ConnectionLogEvent.OPENED, connectionId(id.value()), remoteAddress(context.channel().remoteAddress()));

        output = new ConnectionPacketOutput(
            context,
            warnings,
            id.value(),
            outputBatches,
            outputBytes,
            maxFrame,
            reason -> close(context, reason)
        );
        sessionId = login.open(id.value());
        context.channel().closeFuture().addListener(ignored -> cleanup());
        activity(context);

        var greeting = new PcFirstAccept(0xa815b62300000000L, 0, 0);
        output.send(List.of(new FirstAcceptCodec().encode(greeting)));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, byte[] root) {
        if (phase == ConnectionPhase.CLOSING || phase == ConnectionPhase.CLOSED) {
            return;
        }

        int type = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).getInt();

        LOG.log(ConnectionLogEvent.PACKET_RECEIVED, connectionId(id.value()), packetType(type), rootBytes(root.length));

        RequestEvents.forType(type);

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
            ProtocolDecision decision = protocol.on(RequestEvents.forType(request.type()));

            if (decision == ProtocolDecision.REJECT) {
                exceptionCaught(context, new MalformedPacketException("INVALID_PROTOCOL_STATE"));
                return;
            }

            if (decision == ProtocolDecision.IGNORE) {
                LOG.log(
                    request.type() == PqSelectCharacter.TYPE_ID
                        ? CharacterLogEvent.SELECTION_IGNORED : SessionLogEvent.LOGIN_IGNORED,
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

    private void execute(ChannelHandlerContext context, PendingRequest request) {
        RequestCompletion completion;
        long started = System.nanoTime();

        try (var scope = new LogContext(
            id.value().toString(),
            request.operationId().toString(),
            sessionId.toString()
        )) {
            try {
                completion = requests.dispatch(sessionId, request, output);
            } catch (MalformedPacketException failure) {
                onEventLoop(context, () -> exceptionCaught(context, failure));
                return;
            } catch (RuntimeException failure) {
                if (RoomRequestDispatcher.accepts(request.type())) {
                    LOG.failure(
                        RoomRequestDispatcher.failureEvent(request.type()),
                        failure,
                        durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    );
                    onEventLoop(context, () -> close(context, "ROOM_OPERATION_FAILED"));
                    return;
                }

                if (InventoryRequestDispatcher.accepts(request.type())) {
                    LOG.failure(
                        InventoryLogEvent.MOVE_FAILED,
                        failure,
                        durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    );
                    onEventLoop(context, () -> close(context, "INVENTORY_OPERATION_FAILED"));
                    return;
                }

                if (CharacterRequestDispatcher.accepts(request.type()) || SelectedCharacterRequestDispatcher.accepts(
                    request.type()
                )
                    || LobbyRequestDispatcher.accepts(request.type())) {
                    LOG.failure(
                        CharacterRequestDispatcher.accepts(request.type())
                            ? CharacterRequestDispatcher.failureEvent(request.type())
                            : LobbyRequestDispatcher.accepts(request.type()) ? LobbyRequestDispatcher.failureEvent(
                                request.type()
                            )
                                : SelectedCharacterRequestDispatcher.failureEvent(request.type()),
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
                completion = accounts.failedReply(request, output);
            }
        }

        RequestCompletion result = completion;
        onEventLoop(context, () -> complete(context, result));
    }

    private void onEventLoop(ChannelHandlerContext context, Runnable callback) {
        try {
            context.executor().execute(LogContext.propagate(callback));
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

        if (decision == ProtocolDecision.CLOSE_WITHOUT_REPLY) {
            close(context, "CHARACTER_SELECTION_REJECTED");
            return;
        }

        if (decision != ProtocolDecision.IGNORE) {
            activity(context);
        }

        boolean terminal = decision == ProtocolDecision.CLOSE_AFTER_REPLY;

        if (terminal) {
            closeReason = completion.event() == ProtocolEvent.LOGIN_REJECTED
                ? "LOGIN_REJECTED" : "REGISTRATION_COMPLETED";
            releaseMemberships(closeReason);
        }

        completion.delivery().whenComplete(
            (ignored, failure) -> onEventLoop(
                context,
                () -> {
                    if (phase != ConnectionPhase.EXECUTING) {
                        return;
                    }

                    deadline.cancel(false);
                    activeTask = null;

                    if (failure != null) {
                        close(context, "WRITE_FAILED");
                    } else if (terminal) {
                        close(context, closeReason);
                    } else {
                        pending.removeFirst();
                        phase = ConnectionPhase.OPEN;
                        next(context);
                    }
                }
            )
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
    }

    private void close(ChannelHandlerContext context, String reason) {
        if (phase == ConnectionPhase.CLOSING || phase == ConnectionPhase.CLOSED) {
            return;
        }

        phase = ConnectionPhase.CLOSING;
        protocol.on(ProtocolEvent.CONNECTION_CLOSED);
        closeReason = reason;

        if (output != null) {
            output.close();
        }

        releaseMemberships(reason);
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
        if (phase == ConnectionPhase.CLOSED) {
            return;
        }

        phase = ConnectionPhase.CLOSED;
        protocol.on(ProtocolEvent.CONNECTION_CLOSED);
        releaseMemberships(closeReason);

        if (output != null) {
            output.close();
        }

        cancelWork(closeReason);
        pending.clear();

        if (deadline != null) {
            deadline.cancel(false);
        }

        if (idleDeadline != null) {
            idleDeadline.cancel(false);
        }

        LOG.log(ConnectionLogEvent.CLOSED, connectionId(id.value()), reason(closeReason));
    }

    private void releaseMemberships(String reason) {
        login.close(reason);
        rooms.close(sessionId, reason);
        lobby.close(sessionId, reason);
    }

    private void cancelWork(String reason) {
        RequestTask task = activeTask;
        activeTask = null;

        if (task != null && task.cancel()) {
            executor.remove(task);
            LogEvent event = switch (pending.peekFirst().type()) {
                case PqUserLogin.TYPE_ID -> SessionLogEvent.LOGIN_CANCELLED;
                case PqMoveInventoryItem.TYPE_ID -> InventoryLogEvent.REQUEST_CANCELLED;
                case PqGameSessionCreate.TYPE_ID, PqGameSessionEnter.TYPE_ID, PqGameSessionQuickJoin.TYPE_ID,
                    PqGameSessionChangeTeam.TYPE_ID, PqGameSessionReady.TYPE_ID, PqGameSessionLeave.TYPE_ID,
                    PqGameSessionStartGame.TYPE_ID, PqGameSessionOpenCloseSlot.TYPE_ID ->
                    RoomLogEvent.REQUEST_CANCELLED;
                case PqConfirmName.TYPE_ID, PqNewCharacter.TYPE_ID, PqDelCharacter.TYPE_ID,
                    PqSelectCharacter.TYPE_ID, PqGetChannelList.TYPE_ID, PcUpdateCash.TYPE_ID, PcLogging.TYPE_ID ->
                    CharacterLogEvent.REQUEST_CANCELLED;
                case PqChannelMoveTo.TYPE_ID, PqLobbyEnter.TYPE_ID, PqLobbyLeave.TYPE_ID ->
                    ChannelLogEvent.REQUEST_CANCELLED;
                default -> AccountLogEvent.REQUEST_CANCELLED;
            };
            LOG.log(event, connectionId(id.value()), operationId(task.operationId()), reason(reason));
        }
    }
}

package taan.online.room.controller;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.List;
import java.util.UUID;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.logging.EventLogger;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.protocol.connection.packet.PcNoticeMsg;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomRequestCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PrGameSessionCreateFail;
import taan.online.room.protocol.packet.PrGameSessionEnterFail;
import taan.online.room.protocol.packet.PrGameSessionLeave;
import taan.online.room.service.CreateRoomAction;
import taan.online.room.service.LeaveRoomAction;
import taan.online.room.service.RequestRoomStartAction;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;

public final class RoomPacketHandler {
    private static final EventLogger LOG = EventLogger.forClass(RoomPacketHandler.class);

    private final SelectedCharacterAccess sessions;
    private final CreateRoomAction create;
    private final LeaveRoomAction leave;
    private final RequestRoomStartAction start;
    private final RoomRegistry rooms;

    public RoomPacketHandler(
        SelectedCharacterAccess sessions,
        CreateRoomAction create,
        LeaveRoomAction leave,
        RequestRoomStartAction start,
        RoomRegistry rooms
    ) {
        this.sessions = sessions;
        this.create = create;
        this.leave = leave;
        this.start = start;
        this.rooms = rooms;
    }

    public RoomReply handle(UUID id, int type, byte[] root) {
        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        var codec = new RoomRequestCodec();

        if (type == PqGameSessionEnter.TYPE_ID) {
            codec.enter(root);

            if (!sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            LOG.log(RoomLogEvent.ENTRY_REJECTED, sessionId(id), reason("FEATURE_UNAVAILABLE"));
            return new RoomReply(
                RoomReplyOutcome.ENTRY_REJECTED,
                List.of(new RoomResponseCodec().encode(new PrGameSessionEnterFail(0)))
            );
        }

        if (type == PqGameSessionCreate.TYPE_ID) {
            var request = codec.create(root);
            var info = request.info();
            var settings = info.details();
            var command = settings == null ? new CreateRoomCommand(info.state(), 0, null, 0, 0, 0, 0, 0, false, 0)
                : new CreateRoomCommand(
                    info.state(),
                    settings.roomGrade(),
                    settings.title(),
                    settings.gameKind(),
                    settings.gameType(),
                    settings.rule(),
                    settings.playerCount(),
                    settings.capacity(),
                    settings.password() != null,
                    settings.mapId()
                );
            var result = create.execute(id, command);

            if (result.outcome() == CreateRoomOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            return result.outcome() == CreateRoomOutcome.CREATED
                ? new RoomReply(
                    RoomReplyOutcome.CREATED,
                    List.of(new RoomResponseCodec().encode(new RoomSnapshotMapper().map(result.room())))
                ) : new RoomReply(
                    RoomReplyOutcome.CREATE_REJECTED,
                    List.of(new RoomResponseCodec().encode(new PrGameSessionCreateFail(0)))
                );
        }

        if (type == PqGameSessionLeave.TYPE_ID) {
            var request = codec.leave(root);
            var result = leave.execute(id, request.roomId(), request.reason());
            var outcome = switch (result.outcome()) {
                case LEFT -> RoomReplyOutcome.LEFT;
                case REPLAYED -> RoomReplyOutcome.LEAVE_REPLAYED;
                case REJECTED -> RoomReplyOutcome.LEAVE_REJECTED;
                case IGNORED -> RoomReplyOutcome.IGNORED;
            };

            if (outcome == RoomReplyOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            return new RoomReply(
                outcome,
                List.of(
                    new RoomResponseCodec().encode(
                        new PrGameSessionLeave(outcome == RoomReplyOutcome.LEAVE_REJECTED ? 0 : 1)
                    )
                )
            );
        }

        codec.start(root);
        var result = start.execute(id);

        if (result.ignored() || !sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        return new RoomReply(
            RoomReplyOutcome.START_UNAVAILABLE,
            List.of(new NoticeMessageCodec().encode(new PcNoticeMsg(result.message())))
        );
    }

    public void close(UUID id, String reason) {
        rooms.release(id, reason);
    }

    private RoomReply ignored() {
        return new RoomReply(RoomReplyOutcome.IGNORED, List.of());
    }
}

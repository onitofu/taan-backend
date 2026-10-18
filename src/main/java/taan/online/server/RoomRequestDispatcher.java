package taan.online.server;

import java.util.UUID;
import taan.online.logging.LogEvent;
import taan.online.protocol.PacketOutput;
import taan.online.room.controller.RoomPacketHandler;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.packet.PqGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeMap;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionChangeWeapon;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionStartGame;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;

public final class RoomRequestDispatcher {
    private final RoomPacketHandler handler;

    public RoomRequestDispatcher(RoomPacketHandler handler) {
        this.handler = handler;
    }

    RequestCompletion dispatch(int type, UUID id, byte[] root, PacketOutput output) {
        var result = handler.handle(id, type, root, output);
        var event = switch (result.outcome()) {
            case ENTERED -> ProtocolEvent.ROOM_ENTERED;
            case CREATED -> ProtocolEvent.ROOM_CREATED;
            case EXPEL_COMPLETED, SETTINGS_UPDATED, SETTINGS_REJECTED, WEAPON_UPDATED, WEAPON_REJECTED,
                READY_UPDATED, READY_REJECTED,
                CREATE_REJECTED, ENTRY_REJECTED, QUICK_JOIN_REJECTED,
                LEAVE_REJECTED, START_UNAVAILABLE,
                SLOT_AVAILABILITY_UPDATED, SLOT_AVAILABILITY_REJECTED, TEAM_CHANGED, TEAM_UNCHANGED, TEAM_REJECTED ->
                ProtocolEvent.ROOM_REQUEST_COMPLETED;
            case LEFT -> ProtocolEvent.ROOM_LEFT;
            case LEAVE_REPLAYED -> ProtocolEvent.ROOM_LEAVE_REPLAYED;
            case IGNORED -> ProtocolEvent.ROOM_REQUEST_IGNORED;
        };
        return new RequestCompletion(event, result.delivery());
    }

    void close(UUID id, String reason) {
        handler.close(id, reason);
    }

    static boolean accepts(int type) {
        return type == PqGameSessionUserExpel.TYPE_ID || type == PqGameSessionCreate.TYPE_ID
            || type == PqGameSessionEnter.TYPE_ID
            || type == PqGameSessionQuickJoin.TYPE_ID || type == PqGameSessionLeave.TYPE_ID
            || type == PqGameSessionStartGame.TYPE_ID || type == PqGameSessionOpenCloseSlot.TYPE_ID
            || type == PqGameSessionChangeTeam.TYPE_ID || type == PqGameSessionReady.TYPE_ID
            || type == PqGameSessionChangeWeapon.TYPE_ID
            || type == PqGameSessionChangeMap.TYPE_ID || type == PqGameSessionChangeGameOption.TYPE_ID;
    }

    static LogEvent failureEvent(int type) {
        if (type == PqGameSessionUserExpel.TYPE_ID) {
            return RoomLogEvent.EXPEL_FAILED;
        }

        if (type == PqGameSessionChangeMap.TYPE_ID || type == PqGameSessionChangeGameOption.TYPE_ID) {
            return RoomLogEvent.SETTINGS_FAILED;
        }

        if (type == PqGameSessionChangeWeapon.TYPE_ID) {
            return RoomLogEvent.WEAPON_FAILED;
        }

        if (type == PqGameSessionReady.TYPE_ID) {
            return RoomLogEvent.READY_FAILED;
        }

        if (type == PqGameSessionChangeTeam.TYPE_ID) {
            return RoomLogEvent.TEAM_CHANGE_FAILED;
        }

        if (type == PqGameSessionOpenCloseSlot.TYPE_ID) {
            return RoomLogEvent.SLOT_AVAILABILITY_FAILED;
        }

        if (type == PqGameSessionQuickJoin.TYPE_ID) {
            return RoomLogEvent.QUICK_JOIN_FAILED;
        }

        return type == PqGameSessionCreate.TYPE_ID ? RoomLogEvent.CREATION_FAILED
            : type == PqGameSessionEnter.TYPE_ID ? RoomLogEvent.ENTRY_FAILED : RoomLogEvent.OPERATION_FAILED;
    }
}

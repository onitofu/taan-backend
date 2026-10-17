package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.LeaveRoomResult;

public final class LeaveRoomAction {
    private final RoomRegistry rooms;

    public LeaveRoomAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public LeaveRoomResult execute(UUID id, int roomId, int reason) {
        return rooms.leave(id, roomId, reason);
    }
}

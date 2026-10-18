package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomStartResult;

public final class RequestRoomStartAction {
    private final RoomRegistry rooms;

    public RequestRoomStartAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public RoomStartResult execute(UUID id) {
        return rooms.requestStart(id);
    }
}

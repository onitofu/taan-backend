package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomStartResult;

public final class BeginCaptainSelectionAction {
    private final RoomRegistry rooms;

    public BeginCaptainSelectionAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public RoomStartResult execute(UUID id) {
        return rooms.beginCaptainSelection(id);
    }
}

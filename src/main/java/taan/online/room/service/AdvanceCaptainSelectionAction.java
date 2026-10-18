package taan.online.room.service;

import taan.online.room.service.model.RoomSelectionToken;

public final class AdvanceCaptainSelectionAction {
    private final RoomRegistry rooms;

    public AdvanceCaptainSelectionAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public void execute(RoomSelectionToken token) {
        rooms.advanceCaptainSelection(token);
    }
}

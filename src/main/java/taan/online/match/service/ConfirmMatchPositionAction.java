package taan.online.match.service;

import java.util.UUID;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.model.RoomMatchContext;

public final class ConfirmMatchPositionAction {
    private final RoomMatchAccess rooms;

    public ConfirmMatchPositionAction(RoomMatchAccess rooms) {
        this.rooms = rooms;
    }

    public boolean execute(UUID id, RoomMatchContext context, int slot, int x, int y, int facing) {
        return context != null && context.membership().sessionId().equals(id)
            ? rooms.confirmPosition(context, slot, x, y, facing) : false;
    }
}

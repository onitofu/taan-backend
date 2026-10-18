package taan.online.match.service;

import java.util.UUID;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.model.RoomMatchContext;

public final class ReportMatchEnemiesAction {
    private final RoomMatchAccess rooms;

    public ReportMatchEnemiesAction(RoomMatchAccess rooms) {
        this.rooms = rooms;
    }

    public boolean execute(UUID id, RoomMatchContext context) {
        return context != null && context.membership().sessionId().equals(id)
            ? rooms.reportEnemies(context) : false;
    }
}

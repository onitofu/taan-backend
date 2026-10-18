package taan.online.room.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.ChangeRoomWeaponResult;

public final class ChangeRoomWeaponAction {
    private static final EventLogger LOG = EventLogger.forClass(ChangeRoomWeaponAction.class);

    private final RoomRegistry rooms;

    public ChangeRoomWeaponAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public ChangeRoomWeaponResult execute(UUID id, long characterId, int weaponId) {
        var result = rooms.changeWeapon(id, characterId, weaponId);

        if (result.outcome() == ChangeRoomWeaponOutcome.REJECTED) {
            LOG.log(RoomLogEvent.WEAPON_REJECTED, sessionId(id), reason(result.reason()));
        }

        return result;
    }
}

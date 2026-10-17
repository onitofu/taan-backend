package taan.online.room.controller;

import java.util.ArrayList;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;
import taan.online.room.protocol.packet.GameSessionOption;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;
import taan.online.room.protocol.packet.PcGameSessionInit;
import taan.online.room.service.model.RoomSnapshot;

public final class RoomSnapshotMapper {
    public PcGameSessionInit map(RoomSnapshot room) {
        var settings = room.settings();
        var owner = room.owner();
        var character = owner.character();
        var details = new GameSessionDetails(
            0,
            settings.title().value(),
            0,
            settings.mode() == RoomMode.ITEM ? 0 : 1,
            settings.rule() == RoomRule.BONUS ? 2 : 0,
            1,
            settings.capacity(),
            null,
            settings.mapId()
        );
        var slots = new ArrayList<GameSlotInfo>();

        for (int index = 0; index < settings.capacity(); index++) {
            var occupant = index == owner.slotIndex() ? new GameSlotOccupant(
                character.accountId().value(),
                character.id().value(),
                character.name().value(),
                "",
                character.level(),
                owner.team() == RoomTeam.BLUE ? 1 : 2,
                new CharacterCatalogMapper().appearance(character),
                owner.weaponId()
            ) : null;
            slots.add(new GameSlotInfo(index, occupant == null ? 0 : 2, occupant));
        }

        return new PcGameSessionInit(
            new GameSessionInfo(room.id().value(), 1, details),
            new GameSessionOption(character.id().value(), character.accountId().value()),
            slots,
            0,
            "",
            ""
        );
    }
}

package taan.online.room.view;

import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSlot;
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
        int count = (int) room.slots().stream().filter(slot -> slot.state().occupied()).count();
        var details = new GameSessionDetails(
            0,
            settings.title().value(),
            0,
            settings.mode() == RoomMode.ITEM ? 0 : 1,
            settings.rule() == RoomRule.BONUS ? 2 : 0,
            count,
            settings.capacity(),
            room.protectedRoom() ? "" : null,
            settings.mapId()
        );
        return new PcGameSessionInit(
            new GameSessionInfo(room.id().value(), 1, details),
            option(room),
            room.slots().stream().map(this::slot).toList(),
            0,
            "",
            ""
        );
    }

    public GameSessionOption option(RoomSnapshot room) {
        var owner = room.owner().character();
        return new GameSessionOption(owner.id().value(), owner.accountId().value());
    }

    public GameSlotInfo slot(RoomSlot source) {
        var participant = source.participant();
        GameSlotOccupant occupant = null;

        if (participant != null) {
            var character = participant.character();
            occupant = new GameSlotOccupant(
                character.accountId().value(),
                character.id().value(),
                character.name().value(),
                "",
                character.level(),
                source.team() == RoomTeam.BLUE ? 1 : 2,
                new CharacterCatalogMapper().appearance(character),
                participant.weaponId()
            );
        }

        int state = switch (source.state()) {
            case OPEN -> 0;
            case CLOSED -> 1;
            case OCCUPIED -> 2;
            case READY -> 3;
        };
        return new GameSlotInfo(source.index(), state, occupant);
    }
}

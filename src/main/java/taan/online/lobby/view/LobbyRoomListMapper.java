package taan.online.lobby.view;

import taan.online.room.model.RoomState;
import taan.online.room.protocol.packet.GameSessionInfoPack;
import taan.online.room.service.model.RoomListing;
import taan.online.room.view.RoomRuleMapper;

public final class LobbyRoomListMapper {
    public GameSessionInfoPack map(RoomListing room) {
        if (room.state() == RoomState.CLOSED) {
            throw new IllegalArgumentException("Closed room cannot be listed");
        }

        return new GameSessionInfoPack(
            GameSessionInfoPack.FULL_MASK,
            room.id().value(),
            room.state() == RoomState.WAITING ? 1 : 2,
            0,
            room.title().value(),
            0,
            switch (room.mode()) {
                case ITEM -> 0;
                case NO_ITEM -> 1;
                case CAPTAIN_ITEM -> 2;
                case CAPTAIN_NO_ITEM -> 3;
            },
            new RoomRuleMapper().map(room.rule()),
            room.playerCount(),
            room.capacity(),
            room.mapId(),
            room.protectedRoom() ? "" : null
        );
    }
}

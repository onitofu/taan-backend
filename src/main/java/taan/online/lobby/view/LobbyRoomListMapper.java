package taan.online.lobby.view;

import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomState;
import taan.online.room.protocol.packet.GameSessionInfoPack;
import taan.online.room.service.model.RoomListing;

public final class LobbyRoomListMapper {
    public GameSessionInfoPack map(RoomListing room) {
        if (room.state() != RoomState.WAITING) {
            throw new IllegalArgumentException("Closed room cannot be listed");
        }

        return new GameSessionInfoPack(
            GameSessionInfoPack.FULL_MASK,
            room.id().value(),
            1,
            0,
            room.title().value(),
            0,
            room.mode() == RoomMode.ITEM ? 0 : 1,
            room.rule() == RoomRule.BONUS ? 2 : 0,
            room.playerCount(),
            room.capacity(),
            room.mapId(),
            null
        );
    }
}

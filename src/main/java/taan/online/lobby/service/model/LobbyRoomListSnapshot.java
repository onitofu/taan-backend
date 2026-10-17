package taan.online.lobby.service.model;

import java.util.List;
import taan.online.room.service.model.RoomListing;

public record LobbyRoomListSnapshot(int channelId, List<RoomListing> rooms) {
    public LobbyRoomListSnapshot {
        rooms = List.copyOf(rooms);
    }
}

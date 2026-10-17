package taan.online.lobby.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.lobby.service.model.LobbyRoomListSnapshot;

public interface LobbyRoomListingAccess {
    Optional<LobbyRoomListSnapshot> snapshot(UUID sessionId);
}

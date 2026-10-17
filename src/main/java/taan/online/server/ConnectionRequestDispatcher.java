package taan.online.server;

import java.util.UUID;
import taan.online.protocol.PacketOutput;

final class ConnectionRequestDispatcher {
    private final AccountRequestDispatcher accounts;
    private final CharacterRequestDispatcher characters;
    private final SelectedCharacterRequestDispatcher selected;
    private final LobbyRequestDispatcher lobby;
    private final InventoryRequestDispatcher inventories;
    private final RoomRequestDispatcher rooms;

    ConnectionRequestDispatcher(
        AccountRequestDispatcher accounts,
        CharacterRequestDispatcher characters,
        SelectedCharacterRequestDispatcher selected,
        LobbyRequestDispatcher lobby,
        InventoryRequestDispatcher inventories,
        RoomRequestDispatcher rooms
    ) {
        this.accounts = accounts;
        this.characters = characters;
        this.selected = selected;
        this.lobby = lobby;
        this.inventories = inventories;
        this.rooms = rooms;
    }

    RequestCompletion dispatch(UUID sessionId, PendingRequest request, PacketOutput output) {
        if (RoomRequestDispatcher.accepts(request.type())) {
            return rooms.dispatch(request.type(), sessionId, request.root(), output);
        }

        if (InventoryRequestDispatcher.accepts(request.type())) {
            return inventories.dispatch(sessionId, request.root(), output);
        }

        if (LobbyRequestDispatcher.accepts(request.type())) {
            return lobby.dispatch(request.type(), sessionId, request.root(), output);
        }

        if (SelectedCharacterRequestDispatcher.accepts(request.type())) {
            return selected.dispatch(request.type(), sessionId, request.root(), output);
        }

        if (CharacterRequestDispatcher.accepts(request.type())) {
            return characters.dispatch(request.type(), sessionId, request.root(), output);
        }

        return accounts.dispatch(request, output);
    }

}

package taan.online.lobby.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.lobby.protocol.codec.LobbyRequestCodec;
import taan.online.lobby.service.EnterLobbyAction;
import taan.online.lobby.service.LeaveLobbyAction;
import taan.online.lobby.service.LobbyRoomListingAccess;
import taan.online.lobby.service.model.LobbyResult;
import taan.online.lobby.view.ProtocolLobbyView;
import taan.online.protocol.PacketOutput;

public final class LobbyPacketHandler {
    private final EnterLobbyAction enter;
    private final LeaveLobbyAction leave;
    private final SelectedCharacterAccess sessions;
    private final LobbyRoomListingAccess rooms;
    private final int maxFrameBytes;

    public LobbyPacketHandler(
        EnterLobbyAction enter,
        LeaveLobbyAction leave,
        SelectedCharacterAccess sessions,
        LobbyRoomListingAccess rooms,
        int maxFrameBytes
    ) {
        this.enter = enter;
        this.leave = leave;
        this.sessions = sessions;
        this.rooms = rooms;
        this.maxFrameBytes = maxFrameBytes;
    }

    public LobbyHandlingResult handle(UUID id, byte[] root, boolean entering, PacketOutput output) {
        if (!sessions.hasSelectedCharacter(id)) {
            return new LobbyHandlingResult(LobbyHandlingOutcome.IGNORED, CompletableFuture.completedFuture(null));
        }

        var codec = new LobbyRequestCodec();
        var view = new ProtocolLobbyView(id, rooms, output, maxFrameBytes);
        LobbyResult result;

        if (entering) {
            codec.enter(root);
            result = enter.execute(id, view);
        } else {
            codec.leave(root);
            result = leave.execute(id, view);
        }

        var outcome = switch (result.phase()) {
            case CLOSED -> LobbyHandlingOutcome.IGNORED;
            case LOBBY -> LobbyHandlingOutcome.ENTERED;
            case MEMBER -> LobbyHandlingOutcome.LEFT_MEMBER;
            case NONE -> entering ? LobbyHandlingOutcome.UNAVAILABLE : LobbyHandlingOutcome.LEFT_UNBOUND;
        };
        return new LobbyHandlingResult(outcome, result.delivery());
    }
}

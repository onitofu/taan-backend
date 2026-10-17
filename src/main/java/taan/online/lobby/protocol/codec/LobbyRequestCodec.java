package taan.online.lobby.protocol.codec;

import taan.online.lobby.protocol.packet.PqLobbyEnter;
import taan.online.lobby.protocol.packet.PqLobbyLeave;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;

public final class LobbyRequestCodec {
    public PqLobbyEnter enter(byte[] root) {
        var input = new BinaryReader(root, PqLobbyEnter.TYPE_ID);
        var request = new PqLobbyEnter(input.unsignedByte(), input.unsignedByte(), input.unsignedInt());
        input.finish();

        if (request.firstEnter() > 1 || request.arenaSlot() != 255 || request.arenaId() != 0xffff_ffffL) {
            throw new MalformedPacketException("UNSUPPORTED_LOBBY_PROFILE");
        }

        return request;
    }

    public PqLobbyLeave leave(byte[] root) {
        new BinaryReader(root, PqLobbyLeave.TYPE_ID).finish();
        return new PqLobbyLeave();
    }
}

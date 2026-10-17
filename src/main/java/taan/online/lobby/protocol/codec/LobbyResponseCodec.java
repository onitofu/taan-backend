package taan.online.lobby.protocol.codec;

import java.util.List;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyInitUserList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateUserList;
import taan.online.lobby.protocol.packet.PrLobbyEnter;
import taan.online.lobby.protocol.packet.PrLobbyLeave;
import taan.online.lobby.protocol.packet.UserSimpleInfo;
import taan.online.protocol.BinaryWriter;

public final class LobbyResponseCodec {
    public byte[] enter(PrLobbyEnter response) {
        return new BinaryWriter().unsignedInt(PrLobbyEnter.TYPE_ID).unsignedInt(response.population())
            .unsignedByte(response.result()).toByteArray();
    }

    public byte[] rooms(PcLobbyInitRoomList response) {
        return new BinaryWriter().unsignedInt(PcLobbyInitRoomList.TYPE_ID).unsignedInt(0).toByteArray();
    }

    public byte[] users(PcLobbyInitUserList response) {
        var output = new BinaryWriter().unsignedInt(PcLobbyInitUserList.TYPE_ID);
        users(output, response.users());
        return output.unsignedInt(0).toByteArray();
    }

    public byte[] update(PcLobbyUpdateUserList response) {
        var output = new BinaryWriter().unsignedInt(PcLobbyUpdateUserList.TYPE_ID);
        users(output, response.added());
        output.unsignedInt(response.removed().size());
        response.removed().forEach(output::unsignedInt);
        return output.unsignedInt(0).toByteArray();
    }

    public byte[] leave(PrLobbyLeave response) {
        return new BinaryWriter().unsignedInt(PrLobbyLeave.TYPE_ID).toByteArray();
    }

    private void users(BinaryWriter output, List<UserSimpleInfo> users) {
        output.unsignedInt(users.size());

        for (var user : users) {
            output.unsignedInt(UserSimpleInfo.TYPE_ID).unsignedInt(user.characterId()).wideString(user.name())
                .unsignedShort(user.level()).unsignedByte(user.genderIcon());
        }
    }
}

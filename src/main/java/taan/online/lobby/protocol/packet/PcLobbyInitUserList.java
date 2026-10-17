package taan.online.lobby.protocol.packet;

import java.util.List;

public record PcLobbyInitUserList(List<UserSimpleInfo> users) {
    public static final int TYPE_ID = 0x501d07d9;

    public PcLobbyInitUserList {
        users = List.copyOf(users);
    }
}

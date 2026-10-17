package taan.online.lobby.protocol.packet;

import java.util.List;

public record PcLobbyUpdateUserList(List<UserSimpleInfo> added, List<Long> removed) {
    public static final int TYPE_ID = 0x612308a8;

    public PcLobbyUpdateUserList {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }
}

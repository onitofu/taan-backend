package taan.online.lobby.protocol.packet;

public record PqLobbyEnter(int firstEnter, int arenaSlot, long arenaId) {
    public static final int TYPE_ID = 0x22aa0516;
}

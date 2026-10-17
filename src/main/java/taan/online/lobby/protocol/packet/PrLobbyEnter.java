package taan.online.lobby.protocol.packet;

public record PrLobbyEnter(long population, int result) {
    public static final int TYPE_ID = 0x22b60517;
}

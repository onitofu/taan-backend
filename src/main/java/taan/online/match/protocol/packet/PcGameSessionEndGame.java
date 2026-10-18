package taan.online.match.protocol.packet;

public record PcGameSessionEndGame(GameResult result, int packetFlag) {
    public static final int TYPE_ID = 0x57260801;
}

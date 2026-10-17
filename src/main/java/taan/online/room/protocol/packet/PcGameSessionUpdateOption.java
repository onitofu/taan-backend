package taan.online.room.protocol.packet;

public record PcGameSessionUpdateOption(GameSessionOption option) {
    public static final int TYPE_ID = 0x874e0a4c;
}

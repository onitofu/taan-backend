package taan.online.match.protocol.packet;

public record PrResultAlreadyOpend(int cardIndex, int slotIndex) {
    public static final int TYPE_ID = 0x5b1f0858;
}

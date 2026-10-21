package taan.online.match.protocol.packet;

public record PrResultSelectCard(int cardIndex, int slotIndex) {
    public static final int TYPE_ID = 0x4ad5077a;
}

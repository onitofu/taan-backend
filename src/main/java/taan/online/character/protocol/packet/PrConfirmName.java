package taan.online.character.protocol.packet;

public record PrConfirmName(int result) {
    public static final int TYPE_ID = 0x22f30511;
}

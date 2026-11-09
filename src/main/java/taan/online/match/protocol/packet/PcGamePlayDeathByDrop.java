package taan.online.match.protocol.packet;

public record PcGamePlayDeathByDrop(int slotIndex, int x, int y) {
    public static final String WIRE_NAME = "PcGamePlay_DeathByDrop";
    public static final int TYPE_ID = 0x5db40858;
}

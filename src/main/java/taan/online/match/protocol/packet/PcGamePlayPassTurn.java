package taan.online.match.protocol.packet;

public record PcGamePlayPassTurn(int slotIndex, int x, int y, int facing, long scalar20) {
    public static final int TYPE_ID = 0x470b0762;
}

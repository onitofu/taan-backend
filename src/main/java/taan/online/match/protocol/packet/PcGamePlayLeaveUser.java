package taan.online.match.protocol.packet;

public record PcGamePlayLeaveUser(int slotIndex) {
    public static final int TYPE_ID = 0x4e3507ae;
}

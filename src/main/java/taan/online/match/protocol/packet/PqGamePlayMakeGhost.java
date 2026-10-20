package taan.online.match.protocol.packet;

public record PqGamePlayMakeGhost(int slotIndex) {
    public static final String WIRE_NAME = "PqGamePlay_MakeGhost";
    public static final int TYPE_ID = 0x4ed007b3;
}

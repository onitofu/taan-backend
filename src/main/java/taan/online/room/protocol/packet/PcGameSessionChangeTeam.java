package taan.online.room.protocol.packet;

public record PcGameSessionChangeTeam(
    long accountId,
    int team,
    int oldSlot0,
    int oldSlot1,
    int newSlot0,
    int newSlot1
) {
    public static final int TYPE_ID = 0x71ee093d;
}

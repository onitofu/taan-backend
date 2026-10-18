package taan.online.room.protocol.packet;

public record PcGameSessionChallengeChief(long characterId) {
    public static final int TYPE_ID = 0x9b2d0ad2;
}

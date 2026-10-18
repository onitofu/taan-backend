package taan.online.room.protocol.packet;

public record PqGameSessionChallengeChief(long characterId) {
    public static final int TYPE_ID = 0x9ca70ae0;
}

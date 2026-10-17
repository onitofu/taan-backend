package taan.online.room.protocol.packet;

public record GameSessionOption(long ownerCharacterId, long ownerAccountId) {
    public static final int TYPE_ID = 0x3b9e06d7;
}

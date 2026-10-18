package taan.online.room.protocol.packet;

public record PqGameSessionChangeWeapon(long characterId, int weaponId) {
    public static final int TYPE_ID = 0x872d0a2e;
}

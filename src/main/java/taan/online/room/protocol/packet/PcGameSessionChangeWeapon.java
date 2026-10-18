package taan.online.room.protocol.packet;

public record PcGameSessionChangeWeapon(long characterId, int weaponId) {
    public static final int TYPE_ID = 0x85cf0a20;
}

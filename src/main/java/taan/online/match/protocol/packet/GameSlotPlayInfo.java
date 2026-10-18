package taan.online.match.protocol.packet;

public record GameSlotPlayInfo(
    int slotIndex,
    int x,
    int y,
    int facing,
    int hp,
    int maxHp,
    long scalar34,
    int weaponId,
    int maxEnergy,
    int scalar3c,
    int initialEnergyGrant
) {
    public static final int TYPE_ID = 0x33e8063e;
}

package taan.online.match.model;

import java.util.Objects;

public record WeaponAttackDefinition(
    WeaponAttackKey key,
    int minPower,
    int maxPower,
    int missileVariant,
    int missileTypeId,
    int animationType
) {
    public WeaponAttackDefinition {
        Objects.requireNonNull(key);

        if (minPower < 0 || maxPower > 100 || minPower > maxPower || missileVariant < 0 || missileVariant > 255
            || missileTypeId < 0 || missileTypeId > 65535 || animationType < 0 || animationType > 255) {
            throw new IllegalArgumentException("Invalid weapon attack definition");
        }
    }

    public WeaponMissileKey missileKey() {
        return new WeaponMissileKey(key.weaponId(), missileTypeId);
    }

    public int specialAnimation() {
        return animationType >= 9 && animationType <= 11 ? 39
            : animationType >= 12 && animationType <= 14 ? 40 : 38;
    }
}

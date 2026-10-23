package taan.online.match.model;

public record WeaponFireDefinition(int missileVariant, int missileTypeId) {
    public WeaponFireDefinition {
        if (missileVariant < 0 || missileVariant > 255 || missileTypeId < 0 || missileTypeId > 65535) {
            throw new IllegalArgumentException("Invalid weapon fire definition");
        }
    }

    public WeaponMissileKey missileKey(int weaponId) {
        return new WeaponMissileKey(weaponId, missileTypeId);
    }
}

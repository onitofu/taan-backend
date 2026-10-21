package taan.online.match.model;

public record WeaponMissileKey(int weaponId, int missileTypeId) {
    public WeaponMissileKey {
        if ((weaponId < 0 || weaponId > 11) && weaponId != 100 || missileTypeId < 0 || missileTypeId > 65535) {
            throw new IllegalArgumentException("Invalid weapon missile identity");
        }
    }
}

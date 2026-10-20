package taan.online.match.model;

public record WeaponAttackKey(int weaponId, int attackKind) {
    public WeaponAttackKey {
        if (weaponId < 0 || weaponId > 11 || attackKind < 0 || attackKind > 2) {
            throw new IllegalArgumentException("Invalid weapon attack identity");
        }
    }
}

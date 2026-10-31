package taan.online.match.model;

public record WeaponDefinition(
    int weaponId,
    int resourceMaxHp,
    int maxEnergy,
    int resourceMove,
    int hitEnergy
) {
    public WeaponDefinition {
        if ((weaponId < 0 || weaponId > 11) && weaponId != 100 || resourceMaxHp < 1
            || maxEnergy < 1 || maxEnergy > 65535 || resourceMove < 1 || hitEnergy < 0 || hitEnergy > 10922) {
            throw new IllegalArgumentException("Invalid match weapon definition");
        }
    }
}

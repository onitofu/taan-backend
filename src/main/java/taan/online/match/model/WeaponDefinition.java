package taan.online.match.model;

import java.util.Objects;

public record WeaponDefinition(int weaponId, String asset, int resourceMaxHp, int maxEnergy, int resourceMove) {
    public WeaponDefinition {
        Objects.requireNonNull(asset);

        if (weaponId < 0 || weaponId > 11 || asset.isBlank() || resourceMaxHp < 1
            || maxEnergy < 1 || maxEnergy > 65535 || resourceMove < 1) {
            throw new IllegalArgumentException("Invalid match weapon definition");
        }
    }
}

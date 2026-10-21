package taan.online.match.model;

import java.util.Objects;

public record MissileTerrainDefinition(
    int weaponId,
    int missileTypeId,
    int centerX,
    int centerY,
    TerrainLayerDefinition mask
) {
    public MissileTerrainDefinition {
        Objects.requireNonNull(mask);

        if ((weaponId < 0 || weaponId > 11) && weaponId != 100 || missileTypeId < 0 || missileTypeId > 65535
            || centerX < 0 || centerX >= mask.width()
            || centerY < 0 || centerY >= mask.height() || mask.left() != 0 || mask.top() != 0) {
            throw new IllegalArgumentException("Invalid missile terrain definition");
        }
    }
}

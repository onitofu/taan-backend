package taan.online.match.model;

import java.util.List;
import java.util.Objects;

public record WeaponAttackDefinition(
    WeaponAttackKey key,
    int minPower,
    int maxPower,
    List<WeaponFireDefinition> fires,
    int animationType,
    boolean intrinsicGuidance
) {
    public WeaponAttackDefinition {
        Objects.requireNonNull(key);
        fires = List.copyOf(fires);

        if (minPower < 0 || maxPower > 100 || minPower > maxPower || animationType < 0 || animationType > 255
            || fires.isEmpty() || fires.stream().map(WeaponFireDefinition::missileVariant).distinct().count()
                != fires.size()) {
            throw new IllegalArgumentException("Invalid weapon attack definition");
        }
    }

    public int specialAnimation() {
        return animationType >= 9 && animationType <= 11 ? 39
            : animationType >= 12 && animationType <= 14 ? 40 : 38;
    }
}

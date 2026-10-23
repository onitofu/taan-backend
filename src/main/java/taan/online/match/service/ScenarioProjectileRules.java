package taan.online.match.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import taan.online.match.model.WeaponAttackDefinition;
import taan.online.match.model.WeaponMissileKey;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioGuidedClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;

final class ScenarioProjectileRules {
    private final WeaponAttackDefinition definition;
    private final int shotLimit;
    private final Map<Integer, ScenarioFireClaim> fires = new HashMap<>();
    private final Set<Integer> terminated = new HashSet<>();

    ScenarioProjectileRules(WeaponAttackDefinition definition, int shotLimit) {
        this.definition = definition;
        this.shotLimit = shotLimit;
    }

    void fire(ScenarioFireClaim fire) {
        require(!fires.containsKey(fire.sequence()), "DUPLICATE_FIRE");
        require(
            fire.weaponId() == definition.key().weaponId() && fire.attackKind() == definition.key().attackKind()
                && fire.sequence() >= 0 && fire.sequence() < shotLimit * definition.fires().size(),
            "INVALID_FIRE"
        );
        var profile = definition.fires().get(fire.sequence() % definition.fires().size());
        require(fire.variant() == profile.missileVariant(), "INVALID_FIRE");
        require(
            Float.isFinite(fire.velocityX()) && Float.isFinite(fire.velocityY())
                && Float.isFinite(fire.accelerationX()) && Float.isFinite(fire.accelerationY()),
            "NONFINITE_FIRE"
        );
        fires.put(fire.sequence(), fire);
    }

    void event(ScenarioProjectileClaim event) {
        reference(event.sequence(), event.variant(), event.shotTick(), event.header().tick());
        require(Float.isFinite(event.terrainFactor()) && event.terrainFactor() >= 0, "INVALID_TERRAIN");

        if (event.header().kind() == 2) {
            require(event.terrainFactor() <= 1, "INVALID_TERRAIN");
        }

        if (event.header().kind() == 2 || event.header().kind() == 3) {
            require(terminated.add(event.sequence()), "DUPLICATE_PROJECTILE_TERMINAL");
        }
    }

    void guided(ScenarioGuidedClaim event) {
        reference(event.sequence(), event.variant(), event.shotTick(), event.header().tick());
    }

    private void reference(int sequence, int variant, int shotTick, long tick) {
        var fire = fires.get(sequence);
        require(fire != null, "MISSING_PROJECTILE");
        require(
            variant == fire.variant() && shotTick == fire.shotTick() && tick >= fire.header().tick(),
            "INVALID_PROJECTILE_REFERENCE"
        );
    }

    boolean hasFire() {
        return !fires.isEmpty();
    }

    Map<Integer, WeaponMissileKey> missiles() {
        return fires.keySet().stream().collect(
            Collectors.toUnmodifiableMap(
                value -> value,
                value -> definition.fires().get(value % definition.fires().size())
                    .missileKey(definition.key().weaponId())
            )
        );
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ScenarioRejectedException(reason);
        }
    }
}

package taan.online.match.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import taan.online.match.model.CannonShotDefinition;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioGuidedClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;

final class ScenarioProjectileRules {
    private final CannonShotDefinition definition;
    private final int shotLimit;
    private final Map<Integer, ScenarioFireClaim> fires = new HashMap<>();
    private final Set<Integer> terminated = new HashSet<>();

    ScenarioProjectileRules(CannonShotDefinition definition, int shotLimit) {
        this.definition = definition;
        this.shotLimit = shotLimit;
    }

    void fire(ScenarioFireClaim fire) {
        require(!fires.containsKey(fire.sequence()), "DUPLICATE_FIRE");
        require(
            fire.weaponId() == definition.weaponId() && fire.attackKind() == definition.attackKind()
                && fire.sequence() >= 0 && fire.sequence() < shotLimit && fire.variant() == definition.missileVariant(),
            "INVALID_FIRE"
        );
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

    Map<Integer, Integer> missiles() {
        return fires.keySet().stream().collect(
            Collectors.toUnmodifiableMap(value -> value, value -> definition.missileTypeId())
        );
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ScenarioRejectedException(reason);
        }
    }
}

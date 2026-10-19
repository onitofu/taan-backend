package taan.online.match.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import taan.online.match.model.CannonShotDefinition;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioClaims;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioJournal;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;

final class ScenarioRules {
    private static final long HORIZON = 120000;

    ScenarioJournal validate(MatchSnapshot match, ScenarioClaims claims, MatchParticipant shooter) {
        var actor = match.actors().get(shooter.sessionId());
        require(actor != null && actor.hp() > 0, "ACTOR_NOT_ALIVE");
        require(
            claims.shooterSlot() == shooter.slotIndex() && claims.scenarioSlot() == shooter.slotIndex()
                && claims.facing() == actor.facing(),
            "INVALID_SHOOTER"
        );
        require(
            claims.selectedItems().isEmpty() && !claims.effectsPresent() && (claims.flags() & 1) == 0
                && match.turn().boxAttemptBudget() == 0,
            "UNSUPPORTED_ATTACK_FEATURE"
        );
        var definition = match.definitions().cannonShot();
        float power = claims.power();
        require(
            Float.isFinite(power) && power >= 0 && power <= 1
                && power * 100 >= definition.minPower() && power * 100 <= definition.maxPower(),
            "INVALID_POWER"
        );
        require(!claims.commands().isEmpty() && claims.commands().size() <= 4096, "INVALID_COMMAND_COUNT");
        var occupied = match.participants().stream().map(MatchParticipant::slotIndex).collect(Collectors.toSet());
        var refitted = new HashSet<>(claims.refittedSlots());
        require(
            refitted.size() == claims.refittedSlots().size() && occupied.containsAll(refitted),
            "INVALID_REFITTED_SLOTS"
        );
        ScenarioFireClaim fire = null;
        long previous = -1;
        long finish = 0;
        long endTick = -1;
        int ends = 0;
        boolean terminated = false;

        for (var command : claims.commands()) {
            var header = command.header();
            require(occupied.contains(header.slot()), "UNKNOWN_ACTOR");
            require(header.tick() >= 0 && header.tick() >= previous && header.tick() <= HORIZON, "INVALID_TIMELINE");
            require(header.duration() >= 0 && header.tick() + header.duration() <= HORIZON, "INVALID_DURATION");
            previous = header.tick();

            if (header.kind() == 8) {
                ends++;
                endTick = header.tick();
                require(header.slot() == shooter.slotIndex(), "INVALID_END_OWNER");
            } else {
                finish = Math.max(finish, header.tick() + header.duration());
            }

            if (Set.of(0, 1, 2, 3, 9).contains(header.kind())) {
                require(header.slot() == shooter.slotIndex(), "INVALID_COMMAND_OWNER");
            }

            switch (command) {
                case ScenarioFireClaim value -> {
                    require(fire == null, "DUPLICATE_FIRE");
                    validateFire(value, definition);
                    fire = value;
                }
                case ScenarioProjectileClaim value -> {
                    require(fire != null, "MISSING_PROJECTILE");
                    require(
                        value.sequence() == fire.sequence() && value.variant() == fire.variant()
                            && value.shotTick() == fire.shotTick(),
                        "INVALID_PROJECTILE_REFERENCE"
                    );

                    require(Float.isFinite(value.terrainFactor()) && value.terrainFactor() >= 0, "INVALID_TERRAIN");

                    if (header.kind() == 2) {
                        require(value.terrainFactor() <= 1, "INVALID_TERRAIN");
                    }

                    if (header.kind() == 2 || header.kind() == 3) {
                        require(!terminated, "DUPLICATE_PROJECTILE_TERMINAL");
                        terminated = true;
                    }
                }
                case ScenarioDamageClaim value -> require(
                    value.damage() >= 0 && value.energy1c() >= 0 && value.energy1c() <= 5
                        && value.energy1e() >= 0 && value.energy1e() <= 30,
                    "INVALID_DAMAGE"
                );
                case ScenarioFallClaim value -> require(
                    header.duration() > 0 && value.startY() >= 0 && value.endY() >= value.startY()
                        && value.endY() <= (long) match.definitions().map().height() + 200,
                    "INVALID_FALL"
                );
                case ScenarioUnsupportedClaim ignored -> throw new ScenarioRejectedException("UNSUPPORTED_FEATURE");
                case ScenarioActorClaim ignored -> { }
            }
        }

        require(fire != null, "MISSING_FIRE");
        require(ends == 1 && endTick == finish + 1000, "INVALID_END");
        return new ScenarioJournal(
            claims.commands(),
            Map.of(fire.sequence(), definition.missileTypeId()),
            claims.refittedSlots(),
            endTick
        );
    }

    private void validateFire(ScenarioFireClaim fire, CannonShotDefinition definition) {
        require(
            fire.weaponId() == definition.weaponId() && fire.attackKind() == definition.attackKind()
                && fire.sequence() == 0 && fire.variant() == definition.missileVariant(),
            "INVALID_FIRE"
        );
        require(
            Float.isFinite(fire.velocityX()) && Float.isFinite(fire.velocityY())
                && Float.isFinite(fire.accelerationX()) && Float.isFinite(fire.accelerationY()),
            "NONFINITE_FIRE"
        );
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ScenarioRejectedException(reason);
        }
    }
}

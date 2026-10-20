package taan.online.match.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.WeaponAttackKey;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioAnimationClaim;
import taan.online.match.service.model.ScenarioClaims;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioEatItemBoxClaim;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioGuidedClaim;
import taan.online.match.service.model.ScenarioJournal;
import taan.online.match.service.model.ScenarioMakeItemBoxClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;

final class ScenarioRules {
    private static final long HORIZON = 120000;
    private static final Set<Integer> SHOOTER_KINDS = Set.of(0, 1, 2, 3, 9, 10, 11, 13, 14, 16);

    ScenarioJournal validate(MatchSnapshot match, ScenarioClaims claims, MatchParticipant shooter) {
        var actor = match.actors().get(shooter.sessionId());
        require(actor != null && actor.hp() > 0, "ACTOR_NOT_ALIVE");
        require(
            claims.shooterSlot() == shooter.slotIndex() && claims.scenarioSlot() == shooter.slotIndex()
                && claims.facing() == actor.facing(),
            "INVALID_SHOOTER"
        );
        var selection = match.attacks().getOrDefault(shooter.sessionId(), MatchAttackSelection.NORMAL);
        var selected = MatchItemSelectionRules.selected(selection.activeItems(), match.captain(shooter.slotIndex()));
        int attackKind = MatchItemSelectionRules.attackKind(selected);
        int shotLimit = MatchItemSelectionRules.shotLimit(selected);
        boolean guided = selected.contains(7);
        boolean special = attackKind == 2;
        require(
            claims.selectedItems().equals(selected) && !claims.effectsPresent()
                && (claims.flags() & 1) == (special ? 1 : 0),
            "UNSUPPORTED_ATTACK_FEATURE"
        );
        require(
            claims.headerEnergyGrant() >= 0 && claims.headerEnergyGrant() <= shooter.maxEnergy(),
            "INVALID_HEADER_ENERGY"
        );
        var definition = match.definitions().attacks().get(new WeaponAttackKey(shooter.weaponId(), attackKind));
        int energy = match.definitions().weapons().get(shooter.weaponId()).hitEnergy();
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
        var projectiles = new ScenarioProjectileRules(definition, shotLimit);
        long previous = -1;
        long finish = 0;
        long endTick = -1;
        int ends = 0;

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

            if (SHOOTER_KINDS.contains(header.kind())) {
                require(header.slot() == shooter.slotIndex(), "INVALID_COMMAND_OWNER");
            }

            switch (command) {
                case ScenarioFireClaim value -> projectiles.fire(value);
                case ScenarioProjectileClaim value -> projectiles.event(value);
                case ScenarioGuidedClaim value -> {
                    require(guided, "UNAUTHORIZED_GUIDANCE");
                    projectiles.guided(value);
                }
                case ScenarioDamageClaim value -> require(
                    value.damage() >= 0 && value.energy1c() >= 0 && value.energy1c() <= energy / 2
                        && value.energy1e() >= 0 && value.energy1e() <= energy * 3,
                    "INVALID_DAMAGE"
                );
                case ScenarioFallClaim value -> require(
                    header.duration() > 0 && value.startY() >= 0 && value.endY() >= value.startY()
                        && value.endY() <= (long) match.definitions().map().height() + 200,
                    "INVALID_FALL"
                );
                case ScenarioMakeItemBoxClaim ignored -> { }
                case ScenarioEatItemBoxClaim ignored -> require(projectiles.hasFire(), "MISSING_PROJECTILE");
                case ScenarioAnimationClaim animation -> {
                    boolean prelude = header.kind() == 14;
                    require(!prelude || special, "UNAUTHORIZED_SPECIAL_ANIMATION");
                    int expected = prelude ? definition.specialAnimation() : definition.animationType();
                    require(animation.animationId() == expected, "INVALID_ATTACK_ANIMATION");
                    require(header.duration() == 0, "INVALID_ANIMATION_DURATION");
                }
                case ScenarioUnsupportedClaim ignored -> throw new ScenarioRejectedException("UNSUPPORTED_FEATURE");
                case ScenarioActorClaim ignored -> {
                    if (header.kind() == 16) {
                        require(header.duration() == 0, "INVALID_ANIMATION_DURATION");
                    }
                }
            }
        }

        require(projectiles.hasFire(), "MISSING_FIRE");
        require(ends == 1 && endTick == finish + 1000, "INVALID_END");
        return new ScenarioJournal(
            claims.commands(),
            projectiles.missiles(),
            claims.refittedSlots(),
            claims.headerEnergyGrant(),
            endTick
        );
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ScenarioRejectedException(reason);
        }
    }
}

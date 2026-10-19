package taan.online.match.controller;

import java.util.Set;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioClaim;
import taan.online.match.service.model.ScenarioClaims;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;

public final class ScenarioClaimsMapper {
    public ScenarioClaims map(PcGamePlayScenario packet) {
        var scene = packet.scenario();
        return new ScenarioClaims(
            packet.shooterSlot(),
            scene.slot(),
            packet.facing(),
            scene.flags(),
            scene.power(),
            scene.selectedItems(),
            !scene.effects().isEmpty(),
            scene.refittedSlots(),
            scene.commands().stream().map(this::claim).toList()
        );
    }

    private ScenarioClaim claim(ScenarioCommand command) {
        var value = command.header();
        int duration = switch (value.kind()) {
            case 1, 2, 3, 6, 9, 14 -> value.scalar10();
            default -> 0;
        };
        var header = new ScenarioEventTime(value.slot(), value.kind(), Integer.toUnsignedLong(value.tick()), duration);

        if (value.kind() >= 10) {
            return new ScenarioUnsupportedClaim(header);
        }

        if (command instanceof ScenarioFireCommand fire && value.kind() == 0) {
            var weapon = fire.weapon();
            return new ScenarioFireClaim(
                header,
                weapon.sequence(),
                weapon.variant(),
                weapon.shotTick(),
                fire.weaponId(),
                fire.attackKind(),
                fire.velocity().x(),
                fire.velocity().y(),
                fire.acceleration().x(),
                fire.acceleration().y()
            );
        }

        var weapon = switch (command) {
            case ScenarioFireCommand fire -> fire.weapon();
            case ScenarioWeaponCommand event -> event.weapon();
            case ScenarioGuidedCommand event -> event.weapon();
            default -> null;
        };

        if (weapon != null && Set.of(1, 2, 3).contains(value.kind())) {
            return new ScenarioProjectileClaim(
                header,
                weapon.sequence(),
                weapon.variant(),
                weapon.shotTick(),
                Float.intBitsToFloat(weapon.scalar24Bits())
            );
        }

        if (command instanceof ScenarioDamageCommand damage && value.kind() == 4) {
            return new ScenarioDamageClaim(
                header,
                damage.damage(),
                damage.simulationHp(),
                damage.energy1c(),
                damage.energy1e()
            );
        }

        if (command instanceof ScenarioNormalCommand normal && value.kind() == 6) {
            return new ScenarioFallClaim(header, normal.scalar14(), normal.scalar18());
        }

        return new ScenarioActorClaim(header);
    }
}

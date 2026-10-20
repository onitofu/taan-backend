package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioFloatPoint;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioTail;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponEffect;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.ScenarioTestWorld;
import taan.online.match.service.ValidateScenarioAction;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;
import taan.online.protocol.MalformedPacketException;

class ScenarioClaimsMapperTest {
    @Test
    void mapsMeaningfulValuesWithoutTreatingMarkerPayloadAsGameRules() {
        var fields = new ScenarioWeaponFields(7, 3, new ScenarioIntPoint(100, 200), -1, 0x3f800000, 255);
        var point = new ScenarioFloatPoint(123, -456);
        List<ScenarioCommand> commands = List.of(
            new ScenarioFireCommand(h(0), fields, 0, 0, point, point, 0, 0),
            new ScenarioFireCommand(h(1), fields, 0, 0, point, point, 0, 0),
            new ScenarioGuidedCommand(h(2), fields, new ScenarioIntPoint(3, 4)),
            new ScenarioWeaponCommand(h(3), fields),
            new ScenarioDamageCommand(h(4), 20, 123456, 2, 3),
            new ScenarioDamageCommand(h(5), 20, 123456, 2, 3),
            new ScenarioNormalCommand(h(6), 100, 200),
            new ScenarioNormalCommand(h(9), 9, 103),
            new ScenarioWeaponCommand(h(7), fields),
            new ScenarioFireCommand(h(8), fields, 0, 0, point, point, 0, 0),
            new ScenarioGuidedCommand(h(13), fields, new ScenarioIntPoint(3, 4)),
            new ScenarioNormalCommand(h(14), 39, 8),
            new ScenarioMakeItemBoxCommand(h(5), -1L, new ScenarioIntPoint(100, 200)),
            new ScenarioEatItemBoxCommand(h(5), -1L, 5, -1)
        );
        var scene = new GameScenario(
            2,
            3,
            commands,
            List.of(5),
            List.of(new ScenarioWeaponEffect(1, 1, 2, 3, 4)),
            3,
            .7f,
            List.of(0, 2),
            -1
        );
        var packet = new PcGamePlayScenario(1, 2, scene, 0, 0, new ScenarioTail(0, 0, 0, 0, 0, 0));
        var claims = new ScenarioClaimsMapper().map(new ScenarioCodec().decode(new ScenarioCodec().encode(packet)));
        var mapped = claims.commands();

        assertEquals(2, claims.shooterSlot());
        assertEquals(2, claims.scenarioSlot());
        assertEquals(1, claims.facing());
        assertEquals(3, claims.flags());
        assertEquals(.7f, claims.power());
        assertEquals(List.of(5), claims.selectedItems());
        assertTrue(claims.effectsPresent());
        assertEquals(List.of(0, 2), claims.refittedSlots());
        var fire = assertInstanceOf(ScenarioFireClaim.class, mapped.getFirst());
        assertEquals(7, fire.sequence());
        assertEquals(3, fire.variant());
        assertEquals(-1, fire.shotTick());
        assertEquals(123, fire.velocityX());
        assertEquals(-456, fire.accelerationY());
        assertEquals(0xffff_ffffL, fire.header().tick());
        assertEquals(0, fire.header().duration());

        for (int index : new int[]{1, 2, 3}) {
            var projectile = assertInstanceOf(ScenarioProjectileClaim.class, mapped.get(index));

            assertEquals(1, projectile.terrainFactor());
            assertEquals(700, projectile.header().duration());
        }

        assertEquals(123456, assertInstanceOf(ScenarioDamageClaim.class, mapped.get(4)).simulationHp());
        assertInstanceOf(ScenarioActorClaim.class, mapped.get(5));
        assertEquals(200, assertInstanceOf(ScenarioFallClaim.class, mapped.get(6)).endY());
        assertInstanceOf(ScenarioActorClaim.class, mapped.get(7));
        assertInstanceOf(ScenarioActorClaim.class, mapped.get(8));
        assertInstanceOf(ScenarioActorClaim.class, mapped.get(9));
        assertInstanceOf(ScenarioUnsupportedClaim.class, mapped.get(10));
        assertEquals(700, mapped.get(11).header().duration());
        assertInstanceOf(ScenarioActorClaim.class, mapped.getLast());
    }

    @Test
    void preparedHandlerValidatesItsOwnDecodedRootAndLogsUnexpectedFailuresOnceWithoutSecrets() throws Exception {
        try (var world = new ScenarioTestWorld(); var logs = new LogCapture()) {
            var handler = new ValidateScenarioPacketHandler(world.action);

            assertEquals(ScenarioAdmissionOutcome.ACCEPTED, handler.handle(world.admission(), world.root).outcome());
            assertThrows(MalformedPacketException.class, () -> handler.handle(world.admission(), new byte[0]));
            var failure = new IllegalStateException("secret-login-password-verifier");
            var failing = new ValidateScenarioPacketHandler(new ValidateScenarioAction((context, member) -> {
                throw failure;
            }));

            assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> failing.handle(world.admission(), world.root))
            );
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
            assertTrue(logs.text().contains("match.operation.failed"));
            assertFalse(logs.text().contains("secret-login-password-verifier"));
            assertFalse(logs.text().contains("ScenarioFireClaim"));
        }
    }

    private ScenarioCommandHeader h(int kind) {
        return new ScenarioCommandHeader(2, kind, -1, 700);
    }
}

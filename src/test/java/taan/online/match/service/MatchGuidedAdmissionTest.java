package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchGuidedTestPackets;
import taan.online.MatchRepeatTestPackets;
import taan.online.TestFixtures;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchGuidedAdmissionTest {
    @Test
    void rejectsUnconfirmedMissingWrongCardsAndActivationAfterSpendWithoutChangingTheWorld() throws Exception {
        for (int invalid = 0; invalid < 7; invalid++) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY); var logs = new LogCapture()) {
                if (invalid != 1) {
                    p.grant(invalid == 2 ? 5 : 7, invalid != 0);
                }

                if (invalid == 6) {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                }

                int index = invalid == 3 ? 255 : invalid == 4 ? -1 : invalid == 5 ? 3 : 0;
                var before = p.f.runtime.snapshot();
                assertFalse(p.energy.activate(p.use(index, 7)));
                assertSame(before, p.f.runtime.snapshot());
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, p.f.callbacks.reason);
                assertTrue(p.f.owner.uses.isEmpty());
                assertTrue(logs.text().contains("match.guided.rejected"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.LOGIN));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("ScenarioGuided"));
            }
        }
    }

    @Test
    void respectsTheDisabledProfileAndIgnoresStaleAdmissionAndForeignChannel() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            p.grant(7, true);
            var before = p.f.runtime.snapshot();
            assertFalse(p.energy.activate(p.use(0, 7)));
            assertSame(before, p.f.runtime.snapshot());
        }

        try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
            p.grant(7, true);
            var command = p.use(0, 7);
            var c = command.context();
            var before = p.f.runtime.snapshot();

            for (var context : List.of(
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId() + 1,
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId(),
                    c.sessionId(),
                    UUID.randomUUID()
                ),
                MatchCommandContext.from(before, p.f.room.players.get(1))
            )) {
                assertFalse(p.energy.activate(new UseMatchItemCommand(
                    context,
                    command.turnRevision(),
                    command.phaseRevision(),
                    0,
                    1,
                    0,
                    0,
                    0,
                    7
                )));
                assertSame(before, p.f.runtime.snapshot());
                assertNull(p.f.callbacks.reason);
            }
        }
    }

    @Test
    void rejectsGuidanceWithoutSelectionAndInvalidProjectileReferencesAtomically() throws Exception {
        for (int invalid = 0; invalid < 5; invalid++) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                if (invalid != 0) {
                    p.grant(7, true);
                    assertTrue(p.energy.activate(p.use(0, 7)));
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                }

                var selected = invalid == 0 ? List.<Integer>of() : List.of(7);
                var scene = MatchGuidedTestPackets.shot(0, 0, selected, 0, 1);
                var commands = new ArrayList<>(scene.scenario().commands());
                int index = 1;
                var original = (ScenarioGuidedCommand) commands.get(index);
                var w = original.weapon();
                commands.set(index, new ScenarioGuidedCommand(
                    new ScenarioCommandHeader(invalid == 1 ? 2 : 0, 13, original.header().tick(), 0),
                    new ScenarioWeaponFields(
                        invalid == 2 ? 1 : w.sequence(),
                        invalid == 3 ? 1 : w.variant(),
                        w.position(),
                        invalid == 4 ? w.shotTick() + 1 : w.shotTick(),
                        w.scalar24Bits(),
                        w.scalar28()
                    ),
                    new ScenarioIntPoint(Integer.MIN_VALUE, Integer.MAX_VALUE)
                ));
                scene = MatchRepeatTestPackets.commands(scene, selected, 0, commands);
                var before = p.f.runtime.snapshot();

                var result = p.apply(scene);
                assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                String reason = switch (invalid) {
                    case 0 -> "UNAUTHORIZED_GUIDANCE";
                    case 1 -> "INVALID_COMMAND_OWNER";
                    case 2 -> "MISSING_PROJECTILE";
                    default -> "INVALID_PROJECTILE_REFERENCE";
                };
                assertEquals(reason, result.reason());
                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertTrue(p.f.owner.spends.isEmpty());
                assertTrue(p.f.guest.scenarios.isEmpty());
            }
        }
    }

    @Test
    void rejectsMissingSpendSpoofedSelectionAndUnconsumedSpendItems() throws Exception {
        for (int invalid = 0; invalid < 3; invalid++) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                p.grant(7, true);
                assertTrue(p.energy.activate(p.use(0, 7)));
                var before = p.f.runtime.snapshot();

                if (invalid == 0) {
                    assertEquals(
                        "MISSING_SPEND",
                        p.apply(MatchGuidedTestPackets.shot(0, 0, List.of(7), 0, 1)).reason()
                    );
                } else if (invalid == 1) {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    assertEquals(
                        "UNSUPPORTED_ATTACK_FEATURE",
                        p.apply(MatchGuidedTestPackets.shot(0, 0, List.of(7, 7), 0, 1)).reason()
                    );
                } else {
                    assertFalse(p.energy.record(p.energy.spend(List.of(
                        new SpentMatchItem(7, 0),
                        new SpentMatchItem(7, 0)
                    ))));
                }

                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertTrue(p.f.owner.spends.isEmpty());
                assertTrue(p.f.guest.scenarios.isEmpty());
            }
        }
    }
}

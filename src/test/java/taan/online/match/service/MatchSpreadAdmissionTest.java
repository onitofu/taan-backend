package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchSpreadTestPackets;
import taan.online.TestFixtures;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchSpreadAdmissionTest {
    @Test
    void rejectsUnconfirmedMissingWrongAndOutOfRangeCardsAndAnActivationAfterSpend() throws Exception {
        for (int item : List.of(3)) {
            for (int invalid = 0; invalid < 7; invalid++) {
                try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY); var logs = new LogCapture()) {
                    if (invalid != 1) {
                        p.grant(invalid == 2 ? 5 : item, invalid != 0);
                    }

                    if (invalid == 6) {
                        assertTrue(p.energy.record(p.energy.spend(List.of())));
                    }

                    int index = invalid == 3 ? 255 : invalid == 4 ? -1 : invalid == 5 ? 3 : 0;
                    var before = p.f.runtime.snapshot();
                    assertFalse(p.energy.activate(p.use(index, item)));
                    assertSame(before, p.f.runtime.snapshot());
                    assertEquals(RoomMatchEndReason.INVALID_SCENARIO, p.f.callbacks.reason);
                    assertTrue(p.f.owner.uses.isEmpty());
                    assertTrue(logs.text().contains("match.spread.rejected"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                }
            }
        }
    }

    @Test
    void respectsTheDisabledProfileAndIgnoresForeignOrStaleMembershipWithoutSpending() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            p.grant(3, true);
            var before = p.f.runtime.snapshot();
            assertFalse(p.energy.activate(p.use(0, 3)));
            assertSame(before, p.f.runtime.snapshot());
        }

        try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
            p.grant(3, true);
            var command = p.use(0, 3);
            var c = command.context();
            var other = p.f.match.participants().get(1);
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
                MatchCommandContext.from(before, other.sessionId())
            )) {
                var foreign = new UseMatchItemCommand(
                    context,
                    command.turnRevision(),
                    command.phaseRevision(),
                    command.slot(),
                    command.team(),
                    0,
                    0,
                    0,
                    3
                );
                assertFalse(p.energy.activate(foreign));
                assertSame(before, p.f.runtime.snapshot());
                assertNull(p.f.callbacks.reason);
            }
        }
    }

    @Test
    void rejectsMissingSpendSpoofedSelectionsAndLatePickupWithoutPublishingAnyPartialDamage() throws Exception {
        for (int invalid = 0; invalid < 4; invalid++) {
            try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
                p.grant(3, true);
                assertTrue(p.energy.activate(p.use(0, 3)));
                var before = p.f.runtime.snapshot();
                var scene = p.shot(1);

                if (invalid == 0) {
                    assertEquals("MISSING_SPEND", p.apply(scene).reason());
                } else {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var commands = new ArrayList<ScenarioCommand>(scene.scenario().commands());

                    if (invalid == 3) {
                        commands.add(new ScenarioEatItemBoxCommand(
                            new ScenarioCommandHeader(0, 11, 1500, 0),
                            987654L,
                            3,
                            30
                        ));
                    }

                    var selected = invalid == 1 ? List.of(5) : invalid == 2 ? List.of(3, 3) : List.of(3);
                    scene = MatchRepeatTestPackets.commands(scene, selected, 0, commands);
                    var result = p.apply(scene);
                    assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                    assertEquals(invalid == 3 ? "PICKUP_NOT_PERMITTED" : "UNSUPPORTED_ATTACK_FEATURE", result.reason());
                }

                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertTrue(p.f.owner.scenarios.isEmpty());
                assertTrue(p.f.guest.scenarios.isEmpty());
                assertTrue(p.f.owner.spends.isEmpty());
            }
        }
    }

    @Test
    void rejectsLaunchesBeyondTheSelectedSpreadBudgetAndOutOfRangeBasePower() throws Exception {
        for (int invalid = 0; invalid < 3; invalid++) {
            try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
                p.grant(3, true);
                assertTrue(p.energy.activate(p.use(0, 3)));
                assertTrue(p.energy.record(p.energy.spend(List.of())));
                var before = p.f.runtime.snapshot();
                int launches = invalid == 0 ? 2 : 1;
                float power = invalid == 1 ? -.01f : invalid == 2 ? 1.01f : .5f;
                var scene = MatchSpreadTestPackets.shot(0, 0, List.of(3), 0, launches, power);

                var result = p.apply(scene);
                assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                assertEquals(invalid == 0 ? "INVALID_FIRE" : "INVALID_POWER", result.reason());
                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertTrue(p.f.guest.scenarios.isEmpty());
            }
        }
    }

    @Test
    void refusesSpreadAsAnEarnedBonusDespiteItsExplicitUseCapability() throws Exception {
        try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
            var context = p.energy.context();
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            var before = p.f.runtime.snapshot();
            var command = new ReportMatchBonusCommand(context, 0, 0, OptionalInt.empty(), -1, 3, -17, -3, -1);

            var result = MatchItemExecution.reportBonus(p.f.runtime, command);
            p.f.run();
            assertFalse(result.toCompletableFuture().join());
            assertSame(before, p.f.runtime.snapshot());
            assertTrue(before.loot().inventory(p.f.room.owner).receipts().isEmpty());
            assertNull(p.f.callbacks.reason);
        }
    }

}

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
import taan.online.TestFixtures;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchRepeatAdmissionTest {
    @Test
    void rejectsInvalidIndicesUnconfirmedOrMissingCardsAndDoesNotMutateThePublishedSnapshot() throws Exception {
        for (int item : new int[]{1, 2}) {
            for (int invalid = 0; invalid < 5; invalid++) {
                try (var p = new MatchRepeatFixture(); var logs = new LogCapture()) {
                    if (invalid != 4) {
                        p.grant(invalid == 3 ? 5 : item, invalid != 2);
                    }

                    var before = p.f.runtime.snapshot();
                    int index = invalid == 0 ? -1 : invalid == 1 ? 3 : 0;
                    assertFalse(p.energy.activate(p.use(index, item)));
                    assertSame(before, p.f.runtime.snapshot());
                    assertEquals(RoomMatchEndReason.INVALID_SCENARIO, p.f.callbacks.reason);
                    assertTrue(p.f.owner.uses.isEmpty());
                    assertTrue(logs.text().contains("match.repeat.rejected"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                }
            }

            try (var disabled = new EnergyRuntimeFixture()) {
                var command = disabled.use();
                assertFalse(disabled.activate(new UseMatchItemCommand(
                    command.context(),
                    command.turnRevision(),
                    command.phaseRevision(),
                    command.slot(),
                    command.team(),
                    0,
                    0,
                    0,
                    item
                )));
            }
        }
    }

    @Test
    void ignoresStaleOrForeignMembershipAndClearsAcceptedSelectionOnDisconnect() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            p.grant(2, true);
            var use = p.use(0, 2);
            var c = use.context();
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
                )
            )) {
                var foreign = new UseMatchItemCommand(
                    context,
                    use.turnRevision(),
                    use.phaseRevision(),
                    use.slot(),
                    use.team(),
                    0,
                    0,
                    0,
                    2
                );
                assertFalse(p.energy.activate(foreign));
                assertSame(before, p.f.runtime.snapshot());
                assertNull(p.f.callbacks.reason);
            }

            assertTrue(p.energy.activate(use));
            var departure = new DepartMatchCommand(c, RoomMatchEndReason.DISCONNECTED);
            var result = p.f.runtime.turnWork(() -> p.f.runtime.outcomes.depart(departure));
            p.f.run();
            assertTrue(result.join());
            assertTrue(p.f.match.attacks().isEmpty());
            assertTrue(p.f.match.loot().inventories().isEmpty());
        }

        try (var fresh = new MatchRepeatFixture()) {
            assertTrue(fresh.f.match.attacks().isEmpty());
            assertTrue(fresh.f.match.loot().inventories().isEmpty());
        }
    }

    @Test
    void rejectsExcessFireMissingSpendAndUnauthorizedSpendWithoutPublishingPartialWorldChanges() throws Exception {
        for (int invalid = 0; invalid < 5; invalid++) {
            try (var p = new MatchRepeatFixture()) {
                p.grant(1, true);
                assertTrue(p.energy.activate(p.use(0, 1)));
                var before = p.f.runtime.snapshot();

                if (invalid == 0) {
                    assertEquals("MISSING_SPEND", p.apply(p.shot(2)).reason());
                } else if (invalid == 1) {
                    assertFalse(p.energy.record(p.energy.spend(List.of(new SpentMatchItem(2, 0)))));
                } else {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var base = p.shot(invalid == 2 ? 3 : 2);
                    var commands = new ArrayList<ScenarioCommand>(base.scenario().commands());

                    if (invalid == 3) {
                        commands.add(new ScenarioNormalCommand(new ScenarioCommandHeader(0, 6, 2800, 1), 0, 99999));
                    } else if (invalid == 4) {
                        commands.add(new ScenarioEatItemBoxCommand(
                            new ScenarioCommandHeader(0, 11, 2800, 0),
                            12345L,
                            2,
                            0
                        ));
                    }

                    base = MatchRepeatTestPackets.commands(base, List.of(1), 0, commands);
                    var result = p.apply(base);
                    assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                    var reason = invalid == 2 ? "INVALID_FIRE" : invalid == 3 ? "INVALID_FALL" : "PICKUP_NOT_PERMITTED";
                    assertEquals(reason, result.reason());
                }

                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertTrue(p.f.owner.scenarios.isEmpty());
                assertTrue(p.f.guest.scenarios.isEmpty());
                assertTrue(p.f.owner.spends.isEmpty());
            }
        }
    }

    @Test
    void refusesPlusTwoAsAnEarnedBonusEvenWhenItsUseCapabilityIsEnabled() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            var context = p.energy.context();
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            var before = p.f.runtime.snapshot();
            var command = new ReportMatchBonusCommand(context, 0, 0, OptionalInt.empty(), -1, 2, -17, -3, -1);

            var result = MatchItemExecution.reportBonus(p.f.runtime, command);
            p.f.run();
            assertFalse(result.toCompletableFuture().join());
            assertSame(before, p.f.runtime.snapshot());
            assertTrue(before.loot().inventory(p.f.room.owner).receipts().isEmpty());
            assertNull(p.f.callbacks.reason);
        }
    }

}

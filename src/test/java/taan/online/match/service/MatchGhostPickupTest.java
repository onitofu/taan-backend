package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.TeamGaugeState;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioTeamGaugeClaim;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class MatchGhostPickupTest {
    @ParameterizedTest
    @EnumSource(RoomMode.class)
    void grantsEachBoxOnceWithoutRevivalAndKeepsInventoryAcrossTurns(RoomMode mode) throws Exception {
        try (var g = new GhostMatchFixture(mode)) {
            g.kill(1, 100, 100);
            g.box(GhostMatchFixture.BOX, 100, 100);
            assertTrue(g.make(1));
            assertTrue(g.target(1, 300, 100));
            g.nextTurn();
            var gauge = mode.usesItems() ? Optional.<ScenarioTeamGaugeClaim>empty() : Optional.of(gauge(1, 0));
            var command = new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                gauge,
                GhostMatchFixture.BOX,
                mode.usesItems() ? 1 : 0
            );
            assertTrue(g.pickup(command));
            assertFalse(g.pickup(command));
            assertFalse(g.snapshot().world().boxes().get(GhostMatchFixture.BOX).active());
            assertEquals(0, g.snapshot().actors().get(g.context(1).sessionId()).hp());

            if (mode.usesItems()) {
                assertEquals(1, g.snapshot().loot().inventory(g.context(1).sessionId()).slots().getFirst());
                assertTrue(g.complete(MatchItemExecution.confirmItemReceipt(
                    g.f.runtime,
                    new ConfirmMatchItemReceiptCommand(g.context(1), 1, 0, 1)
                )));
                assertEquals(MatchItemSource.GHOST_PICKUP, g.snapshot().loot().inventory(g.context(1).sessionId())
                    .receipts().getFirst().source());
                g.nextTurn();
                assertEquals(1, g.snapshot().loot().inventory(g.context(1).sessionId()).slots().getFirst());
                assertTrue(g.complete(MatchItemExecution.discardItem(
                    g.f.runtime,
                    new DiscardMatchItemCommand(g.context(1), 1, 0, 1)
                )));
            } else {
                assertEquals(new TeamGaugeState(5, 5), g.snapshot().world().teamGauges().get(RoomTeam.BLUE));
                assertTrue(g.snapshot().loot().inventory(g.context(1).sessionId()).receipts().isEmpty());
            }

            assertEquals(GhostMatchFixture.BOX, g.f.owner.bonuses.getLast().entityId());
            assertTrue(g.f.owner.closed.isEmpty());
        }
    }

    @Test
    void rejectsUnverifiedForeignAbsentAndInvalidRewardClaimsWithoutConsumingBox() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
            g.kill(1, 100, 100);
            assertTrue(g.make(1));
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
            g.box(GhostMatchFixture.BOX, 100, 100);
            assertTrue(g.target(1, 300, 100));
            g.nextTurn();
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                2,
                Optional.empty(),
                GhostMatchFixture.BOX,
                1
            )));
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX + 1, 1)));
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 6)));
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 9999)));
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(gauge(1, 0)),
                GhostMatchFixture.BOX,
                1
            )));
            assertTrue(g.snapshot().world().boxes().get(GhostMatchFixture.BOX).active());
            assertTrue(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
        }
    }

    @Test
    void consumesBoxWithFullInventoryButDoesNotGrantAFourthItem() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            assertTrue(g.make(1));
            var context = g.context(1);
            var inventory = g.f.match.loot();

            for (int count = 0; count < 3; count++) {
                inventory = inventory.inserted(
                    context.sessionId(),
                    context.admissionId(),
                    0,
                    1,
                    MatchItemSource.GHOST_PICKUP,
                    OptionalLong.empty(),
                    Optional.empty()
                );
            }

            g.f.match.updateLoot(inventory);
            g.f.runtime.refresh();
            g.box(GhostMatchFixture.BOX, 100, 100);
            g.target(1, 300, 100);
            g.nextTurn();
            var before = g.snapshot();
            assertTrue(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
            assertFalse(g.f.match.commitScenario(
                before.world().revision(),
                before.turn().revision(),
                before.world(),
                Optional.empty(),
                before.loot(),
                before.loot()
            ));
            assertEquals(3, g.snapshot().loot().grantSequence());
            assertEquals(3, g.snapshot().loot().inventory(context.sessionId()).receipts().size());
            assertFalse(g.snapshot().world().boxes().get(GhostMatchFixture.BOX).active());
        }
    }

    @Test
    void rebasesConcurrentNoItemClaimsAndStopsGrantingAtTheGaugeLimit() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.NO_ITEM)) {
            g.kill(0, 100, 100);
            g.kill(1, 100, 300);
            assertTrue(g.make(0));
            assertTrue(g.make(1));
            g.target(0, 300, 100);
            g.target(1, 300, 300);
            g.box(GhostMatchFixture.BOX, 100, 100);
            g.box(GhostMatchFixture.BOX + 1, 100, 300);
            g.nextTurn();
            assertTrue(g.pickup(new ReportMatchPickupCommand(
                g.context(0),
                0,
                0,
                Optional.of(gauge(0, 0)),
                GhostMatchFixture.BOX,
                0
            )));
            assertTrue(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(gauge(1, 0)),
                GhostMatchFixture.BOX + 1,
                0
            )));
            assertEquals(10, g.snapshot().world().teamGauges().get(RoomTeam.BLUE).committed());
            assertEquals(5, g.f.owner.bonuses.getLast().gauge().orElseThrow().oldGauge());
            assertEquals(10, g.f.owner.bonuses.getLast().gauge().orElseThrow().newGauge());
        }

        try (var g = new GhostMatchFixture(RoomMode.CAPTAIN_NO_ITEM)) {
            g.kill(1, 100, 100);
            g.make(1);
            g.target(1, 300, 100);
            g.scenario.gauge(RoomTeam.BLUE, 500);
            g.box(GhostMatchFixture.BOX, 100, 100);
            g.nextTurn();
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(gauge(1, 500)),
                GhostMatchFixture.BOX,
                0
            )));
            assertTrue(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 0)));
            assertEquals(500, g.snapshot().world().teamGauges().get(RoomTeam.BLUE).committed());
        }
    }

    private static ScenarioTeamGaugeClaim gauge(int slot, int old) {
        return new ScenarioTeamGaugeClaim(new ScenarioEventTime(slot, 12, 0, 0), 0, 3, old + 5, old);
    }
}

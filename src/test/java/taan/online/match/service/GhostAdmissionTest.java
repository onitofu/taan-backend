package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.model.GhostPoint;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchTurnClosureReason;
import taan.online.match.service.model.ConfirmMatchDropCommand;
import taan.online.match.service.model.ConfirmMatchGhostPositionCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioTeamGaugeClaim;
import taan.online.match.service.model.SetMatchGhostTargetCommand;
import taan.online.room.model.RoomMode;

class GhostAdmissionTest {
    @Test
    void refusesStaleTurnClaimsAndUnreachableBoxesWithoutConsumingThem() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            g.make(1);
            g.target(1, 300, 100);
            g.box(GhostMatchFixture.BOX, 200, 100);
            long previous = g.snapshot().turn().revision();
            g.nextTurn();
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.empty(),
                GhostMatchFixture.BOX,
                1,
                0,
                0,
                0,
                previous
            )));
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
            assertTrue(g.snapshot().world().boxes().get(GhostMatchFixture.BOX).active());
            g.f.timers.now += 1_500_000_000L;
            assertTrue(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 1)));
        }
    }

    @Test
    void validatesEveryNoItemGaugeFieldAndHonorsDisabledLoot() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.NO_ITEM)) {
            g.kill(1, 100, 100);
            g.make(1);
            g.target(1, 300, 100);
            g.box(GhostMatchFixture.BOX, 100, 100);
            g.nextTurn();
            assertFalse(g.pickup(g.pickup(1, GhostMatchFixture.BOX, 0)));

            for (var claim : List.of(
                new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 11, 0, 0), 0, 3, 5, 0),
                new ScenarioTeamGaugeClaim(new ScenarioEventTime(2, 12, 0, 0), 0, 3, 5, 0),
                new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 12, 0, 0), 0, 4, 5, 0),
                new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 12, 0, 0), 0, 3, 6, 1),
                new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 12, 0, 0), 0, 3, 6, 0)
            )) {
                assertFalse(g.pickup(new ReportMatchPickupCommand(
                    g.context(1),
                    1,
                    1,
                    Optional.of(claim),
                    GhostMatchFixture.BOX,
                    0
                )));
            }

            var valid = new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 12, 0, 0), 0, 3, 5, 0);
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(valid),
                GhostMatchFixture.BOX,
                1
            )));
            assertTrue(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(valid),
                GhostMatchFixture.BOX,
                0
            )));
        }

        try (var g = new GhostMatchFixture(RoomMode.NO_ITEM, MatchLootPolicy.disabled())) {
            g.kill(1, 100, 100);
            g.make(1);
            g.target(1, 300, 100);
            g.box(GhostMatchFixture.BOX, 100, 100);
            g.nextTurn();
            assertFalse(g.pickup(new ReportMatchPickupCommand(
                g.context(1),
                1,
                1,
                Optional.of(new ScenarioTeamGaugeClaim(new ScenarioEventTime(1, 12, 0, 0), 0, 3, 5, 0)),
                GhostMatchFixture.BOX,
                0
            )));
        }
    }

    @Test
    void preservesDomainLifecycleGuardsAndIgnoresCommandsForAnOldRuntime() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var context = g.context(1);
            g.kill(1, 100, 100);
            assertFalse(g.f.match.makeGhost(context.sessionId(), 7));
            assertTrue(g.f.match.makeGhost(context.sessionId(), 1));
            assertFalse(g.f.match.makeGhost(context.sessionId(), 1));
            g.f.match.closeTurn(MatchTurnClosureReason.PASS_TURN, g.f.timers.now);
            assertTrue(g.f.match.setGhostTarget(context.sessionId(), new GhostPoint(200, 200)));
            g.f.match.withdraw(context.sessionId(), MatchExitKind.DEPARTED);
            assertFalse(g.f.match.setGhostTarget(context.sessionId(), new GhostPoint(200, 200)));
        }

        try (var f = new MatchOutcomeFixture()) {
            var old = MatchCommandContext.from(f.context(f.room.owner));
            var stale = new MatchCommandContext(
                old.matchId(),
                old.roomId(),
                UUID.randomUUID(),
                old.channelId(),
                old.sessionId(),
                old.admissionId()
            );
            var action = new ReportMatchGhostAction(f.matches);
            assertFalse(action.drop(new ConfirmMatchDropCommand(stale, 0, 100, 1050)).toCompletableFuture().join());
            assertFalse(action.target(new SetMatchGhostTargetCommand(stale, 0, 100, 100)).toCompletableFuture().join());
            assertFalse(action.position(new ConfirmMatchGhostPositionCommand(stale, 0, 100, 100, 0))
                .toCompletableFuture().join());
        }
    }
}

package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.GhostPoint;
import taan.online.match.model.GhostMovementRules;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchLifecycleEvent;
import taan.online.match.service.model.ConfirmMatchGhostPositionCommand;
import taan.online.match.service.model.MakeMatchGhostCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.SetMatchGhostTargetCommand;
import taan.online.room.model.RoomMode;

class MatchGhostLifecycleTest {
    @Test
    void createsOnceAfterDeathAndMovesOnlyAtTurnStartWithoutRevival() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM); var logs = new LogCapture()) {
            assertFalse(g.make(1));
            assertFalse(g.target(1, 300, 200));
            assertFalse(g.position(1, 300, 200, 0));
            g.kill(1, 100, 100);
            assertTrue(g.make(1));
            var created = g.snapshot();
            assertTrue(g.make(1));
            assertSame(created, g.snapshot());
            assertEquals(1, g.f.owner.ghosts.size());
            assertTrue(g.target(1, 700, 100));
            var ghost = g.snapshot().ghosts().get(g.context(1).sessionId());
            assertEquals(new GhostPoint(100, 100), ghost.position());
            assertTrue(ghost.segment().isEmpty());
            assertTrue(g.position(1, 100, 100, 0));
            assertTrue(g.position(1, 999, 999, 1));
            assertEquals(3, g.f.owner.ghosts.size());

            g.nextTurn();
            ghost = g.snapshot().ghosts().get(g.context(1).sessionId());
            assertEquals(1500, ghost.segment().orElseThrow().durationMillis());
            assertEquals(new GhostPoint(300, 100), ghost.segment().orElseThrow().end());
            assertTrue(g.position(1, 1200, 800, 1));
            assertEquals(3, g.f.owner.ghosts.size());
            g.f.timers.now += 1_600_000_000L;
            assertTrue(g.position(1, 300, 100, 0));
            ghost = g.snapshot().ghosts().get(g.context(1).sessionId());
            assertEquals(new GhostPoint(300, 100), ghost.position());
            assertTrue(ghost.segment().isEmpty());
            assertEquals(0, g.snapshot().actors().get(g.context(1).sessionId()).hp());
            assertTrue(g.position(1, 300, 100, 1));
            assertTrue(g.position(1, 300, 101, 0));
            assertTrue(logs.text().contains("match.ghost.position.corrected"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    @Test
    void startsAnEarlyNextTurnFromTheCurrentPositionInsteadOfTeleportingToThePreviousEndpoint() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            assertTrue(g.make(1));
            assertTrue(g.target(1, 300, 100));
            g.nextTurn();
            var before = g.snapshot().ghosts().get(g.context(1).sessionId()).segment().orElseThrow();
            g.f.timers.now += 300_000_000L;
            var expected = GhostMovementRules.positionAt(before, g.f.timers.now / 1000000);

            g.nextTurn();

            var next = g.snapshot().ghosts().get(g.context(1).sessionId());
            assertNotEquals(before.end(), expected);
            assertEquals(expected, next.position());
            assertEquals(expected, next.segment().orElseThrow().start());
        }
    }

    @Test
    void rejectsForeignSlotsOutOfMapTargetsDeparturesAndTerminalWork() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var context = g.context(1);
            var foreign = new MakeMatchGhostCommand(context, 2);
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.ghosts.make(foreign))));
            g.kill(1, 100, 100);
            assertTrue(g.make(1));

            for (int[] point : new int[][]{{-1, 100}, {100, -1}, {1300, 100}, {100, 950}}) {
                assertFalse(g.target(1, point[0], point[1]));
            }

            var stale = new MatchCommandContext(
                context.matchId(),
                context.roomId(),
                context.roomGeneration(),
                context.channelId(),
                context.sessionId(),
                UUID.randomUUID()
            );
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.ghosts.target(
                new SetMatchGhostTargetCommand(stale, 1, 100, 100),
                g.f.timers.now
            ))));
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.ghosts.position(
                new ConfirmMatchGhostPositionCommand(stale, 1, 100, 100, 0),
                g.f.timers.now
            ))));

            g.nextTurn();
            g.f.match.withdraw(context.sessionId(), MatchExitKind.DEPARTED);
            g.f.runtime.refresh();
            assertTrue(g.snapshot().ghosts().isEmpty());
            assertFalse(g.make(1));
            assertTrue(g.snapshot().ghostPickupWindow().orElseThrow().alternatives().isEmpty());
            g.f.match.exclude(Set.of(g.context(3).sessionId()));
            g.f.match.transition(MatchLifecycleEvent.TERMINATE);
            g.f.runtime.refresh();
            assertTrue(g.snapshot().ghostPickupWindow().isEmpty());
            assertTrue(g.snapshot().teamItems().isEmpty());
            assertFalse(g.target(1, 100, 100));
        }
    }
}

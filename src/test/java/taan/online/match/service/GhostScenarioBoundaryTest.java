package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.match.model.GhostPoint;
import taan.online.match.service.model.MakeMatchGhostCommand;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;

class GhostScenarioBoundaryTest {
    @Test
    void requiresTheInitialMotionDelayAndFencesQueuedCreationOnDisconnect() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            g.make(1);
            g.target(1, 300, 100);
            g.nextTurn();
            var command = g.scenario.shot();
            var result = g.scenario.apply(command);
            assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
            assertEquals("MISSING_GHOST_DELAY", result.reason());
        }

        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            var command = new MakeMatchGhostCommand(g.context(1), 1);
            var pending = g.f.runtime.turnWork(() -> g.f.runtime.ghosts.make(command));
            g.f.runtime.stop();
            g.f.runtime.advanceGhosts();
            g.f.run();
            assertFalse(pending.join());
            assertTrue(g.f.match.ghosts().isEmpty());
            g.f.match.cancel();
            assertFalse(g.f.match.makeGhost(g.context(1).sessionId(), 1));
            assertFalse(g.f.match.setGhostTarget(g.context(1).sessionId(), new GhostPoint(100, 100)));
        }
    }

    @Test
    void updatesTheGhostPickupWindowsKnownGaugeValuesWhenPowerQuizChangesTheSameTeam() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.NO_ITEM, MatchLootPolicy.nativeItems(), RoomRule.POWER_QUIZ)) {
            g.kill(1, 100, 100);
            assertTrue(g.make(1));
            g.target(1, 300, 100);
            g.nextTurn();
            g.f.timers.now += 1_500_000_000L;
            assertEquals(ScenarioApplicationOutcome.APPLIED, g.scenario.apply(g.scenario.shot()).outcome());
            assertTrue(g.scenario.report(g.scenario.quiz(0, 0)));
            var window = g.snapshot().ghostPickupWindow().orElseThrow();
            assertTrue(window.observedGauges().get(RoomTeam.BLUE).contains(5));
            var ghost = g.snapshot().ghosts().get(g.context(1).sessionId());
            assertEquals(new GhostPoint(300, 100), ghost.position());
            assertFalse(ghost.queuedForShot());
        }
    }
}

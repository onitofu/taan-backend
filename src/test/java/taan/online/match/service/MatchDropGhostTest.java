package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchTurnClosureReason;
import taan.online.match.service.model.ConfirmMatchDropCommand;
import taan.online.match.service.model.MatchGhostNoticeKind;
import taan.online.room.model.RoomMode;

class MatchDropGhostTest {
    @Test
    void confirmsOnlyTheOwnersTrackedFallAndCreatesAGhostWithoutDisconnecting() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var context = g.context(0);
            int x = g.snapshot().actors().get(context.sessionId()).x();
            int height = g.snapshot().definitions().map().height();
            var command = new ConfirmMatchDropCommand(context, 0, x, height + 100);
            assertFalse(drop(g, command));
            assertTrue(g.fall(height + 100));
            var timer = g.f.timers.commands.getFirst();
            assertFalse(drop(g, new ConfirmMatchDropCommand(g.context(2), 2, x, height + 100)));
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 1, x, height + 100)));
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 0, x + 1, height + 100)));
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 0, x, height - 1)));
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 0, x, height + 201)));
            assertTrue(drop(g, command));
            assertEquals(0, g.snapshot().actors().get(context.sessionId()).hp());
            assertFalse(g.snapshot().turn().activeSlot() == 0);
            assertTrue(g.f.owner.ghosts.isEmpty());
            assertEquals(MatchGhostNoticeKind.DROP, g.f.guest.ghosts.getFirst().kind());
            assertTrue(g.make(0));
            assertFalse(drop(g, command));
            assertTrue(g.f.owner.closed.isEmpty());
            long revision = g.snapshot().turn().revision();
            timer.run();
            g.f.run();
            assertEquals(revision, g.snapshot().turn().revision());
        }
    }

    @Test
    void handlesClosingFallsAndIgnoresAlreadySettledOrInMapFalls() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var context = g.context(0);
            int height = g.snapshot().definitions().map().height();
            int x = g.snapshot().actors().get(context.sessionId()).x();
            assertTrue(g.fall(height + 100));
            g.f.match.closeTurn(MatchTurnClosureReason.ACTION_TIMEOUT, g.f.timers.now);
            g.f.runtime.refresh();
            assertTrue(drop(g, new ConfirmMatchDropCommand(context, 0, x, height + 100)));
            assertEquals(0, g.snapshot().actors().get(context.sessionId()).hp());
        }

        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var context = g.context(0);
            var actor = g.snapshot().actors().get(context.sessionId());
            assertTrue(g.fall(actor.y() - 20));
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 0, actor.x(), 1000)));
            assertTrue(g.fall(1050));
            g.f.match.settleMovement(Long.MAX_VALUE);
            g.f.runtime.refresh();
            assertFalse(drop(g, new ConfirmMatchDropCommand(context, 0, actor.x(), 1050)));
            g.f.match.cancel();
            assertFalse(g.f.match.confirmDrop(context.sessionId(), actor.x(), 1050, 0));
        }
    }

    private boolean drop(GhostMatchFixture g, ConfirmMatchDropCommand command) {
        return g.complete(g.f.runtime.turnWork(() -> g.f.runtime.ghosts.drop(command, g.f.timers.now)));
    }
}

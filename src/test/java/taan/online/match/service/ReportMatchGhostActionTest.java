package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMembership;

class ReportMatchGhostActionTest {
    @Test
    void ignoresMissingForeignAndObsoleteBindingsWithoutCreatingStateOrClosingASession() throws Exception {
        try (var f = new MatchOutcomeFixture(); var logs = new LogCapture()) {
            var action = new ReportMatchGhostAction(f.matches);
            var id = f.room.owner;
            var before = f.snapshot();
            var context = f.context(id);
            action.execute(id, null, 0).toCompletableFuture().join();
            action.execute(UUID.randomUUID(), context, 0).toCompletableFuture().join();
            action.execute(id, context, 0).toCompletableFuture().join();
            action.execute(id, context, 7).toCompletableFuture().join();

            assertSame(before, f.snapshot());
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(f.guest.closed.isEmpty());
            f.surrender(id).toCompletableFuture().join();
            action.execute(id, context, 0).toCompletableFuture().join();
            action.execute(id, context, 0).toCompletableFuture().join();
            assertEquals(1, f.owner.ends);
            assertEquals(1, f.guest.ends);
            assertTrue(logs.text().contains("STALE_OR_INAPPLICABLE"));
            assertTrue(logs.text().contains("STALE_CONTEXT"));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));

            f.room.scenario.rooms.setReady(f.room.players.get(1), true);
            f.start();
            var fresh = f.snapshot();
            action.execute(id, context, 0).toCompletableFuture().join();
            assertSame(fresh, f.snapshot());
        }
    }

    @Test
    void keepsTheConfirmedLoserConnectedUntilBothScenarioAcknowledgementsAndReturnsBothPlayers() throws Exception {
        try (var f = new MatchOutcomeFixture(); var logs = new LogCapture()) {
            var action = new ReportMatchGhostAction(f.matches);
            var shooter = f.active();
            var victim = f.snapshot().participants().stream().filter(p -> !p.sessionId().equals(shooter))
                .findFirst().orElseThrow();
            var context = f.context(victim.sessionId());
            assertEquals(ScenarioApplicationOutcome.APPLIED, f.shot(10000, false, false).outcome());
            var committed = f.snapshot();

            for (int repeat = 0; repeat < 3; repeat++) {
                action.execute(victim.sessionId(), context, victim.slotIndex()).toCompletableFuture().join();
                assertSame(committed, f.snapshot());
            }

            assertTrue(logs.text().contains("SPECTATOR"));
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(f.guest.closed.isEmpty());
            f.ack(shooter);
            assertEquals(0, f.owner.ends);
            f.ack(victim.sessionId());
            action.execute(victim.sessionId(), context, victim.slotIndex()).toCompletableFuture().join();
            assertEquals(1, f.owner.ends);
            assertEquals(1, f.guest.ends);
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(f.guest.closed.isEmpty());
            assertTrue(f.matches.current(f.token).isEmpty());
        }
    }

    @Test
    void revalidatesAdmissionBeforeInspectingTheActor() {
        try (var f = new MatchOutcomeFixture()) {
            var before = f.snapshot();
            var context = f.context(f.room.owner);
            var m = context.membership();
            var stale = new RoomMatchContext(
                new RoomMembership(m.sessionId(), m.roomId(), m.generation(), m.channelId(), UUID.randomUUID()),
                context.token()
            );
            new ReportMatchGhostAction(f.matches).execute(f.room.owner, stale, 0).toCompletableFuture().join();
            assertSame(before, f.snapshot());
        }
    }
}

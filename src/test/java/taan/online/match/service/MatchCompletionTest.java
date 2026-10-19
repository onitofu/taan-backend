package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchCompletionTest {
    @Test
    void requiresTwoAuthenticatedAcknowledgementsAndAcceptsTheCoalescedShooterAckOnlyOnce() throws Exception {
        try (var fixture = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            var first = f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, first.join().outcome());
            var before = f.runtime.snapshot();
            var owner = fixture.ack(0, true);
            var guest = fixture.ack(1, false);
            var firstAck = f.runtime.confirmScenario(owner);
            var duplicate = f.runtime.confirmScenario(owner);
            var observerWithoutReceipt = f.runtime.confirmScenario(fixture.ack(1, true));
            f.run();

            assertTrue(firstAck.join());
            assertFalse(duplicate.join());
            assertFalse(observerWithoutReceipt.join());
            assertEquals(1, f.runtime.snapshot().completion().orElseThrow().awaitingSessions().size());
            assertEquals(1, f.owner.turns.size());
            assertSame(before.world(), f.runtime.snapshot().world());

            var last = f.runtime.confirmScenario(guest);
            f.run();
            assertTrue(last.join());
            var after = f.runtime.snapshot();
            assertEquals(MatchPhase.ACTION, after.phase());
            assertEquals(2, after.turn().activeSlot());
            assertEquals(2, after.turn().revision());
            assertEquals(1, after.turn().round());
            assertEquals(before.turn().windRaw(), after.turn().windRaw());
            assertEquals(2, f.owner.turns.size());
            assertEquals(2, f.guest.turns.size());
            assertTrue(logs.text().contains("match.scenario.completed"));
            assertTrue(logs.text().contains("round="));
            assertFalse(logs.text().contains("123456"));

            var old = f.runtime.confirmScenario(guest);
            f.run();
            assertFalse(old.join());
            assertEquals(2, f.owner.turns.size());

            var second = f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, second.join().outcome());
            assertEquals(923, f.runtime.snapshot().actors().get(f.room.owner).hp());
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertEquals(3, f.runtime.snapshot().turn().revision());
            assertEquals(2, f.runtime.snapshot().turn().round());
            assertTrue(Math.abs(f.runtime.snapshot().turn().windRaw()) <= 20);
            assertEquals(10, f.runtime.snapshot().turn().energyGrant());
        }
    }

    @Test
    void ignoresForgedSlotsReceiptsWorldsTurnsAndStaleTimers() throws Exception {
        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            var oldTimer = f.timers.commands.getFirst();
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            var ack = fixture.ack(0, false);
            var before = f.runtime.snapshot();

            for (var candidate : new ConfirmScenarioCommand[]{
                new ConfirmScenarioCommand(
                    ack.context(),
                    ack.turnRevision() + 1,
                    ack.worldRevision(),
                    ack.fingerprint(),
                    0
                ),
                new ConfirmScenarioCommand(
                    ack.context(),
                    ack.turnRevision(),
                    ack.worldRevision() + 1,
                    ack.fingerprint(),
                    0
                ),
                new ConfirmScenarioCommand(
                    ack.context(),
                    ack.turnRevision(),
                    ack.worldRevision(),
                    Optional.of("bad"),
                    0
                ),
                new ConfirmScenarioCommand(
                    ack.context(),
                    ack.turnRevision(),
                    ack.worldRevision(),
                    ack.fingerprint(),
                    2
                ),
                new ConfirmScenarioCommand(ack.context(), ack.turnRevision(), ack.worldRevision(), Optional.empty(), 0)
            }) {
                var result = f.runtime.confirmScenario(candidate);
                f.run();
                assertFalse(result.join());
            }

            f.admitted = false;
            var revoked = f.runtime.confirmScenario(ack);
            f.run();
            assertFalse(revoked.join());
            f.admitted = true;
            oldTimer.run();
            f.run();
            assertSame(before.world(), f.runtime.snapshot().world());
            assertEquals(2, f.runtime.snapshot().completion().orElseThrow().awaitingSessions().size());
            f.timers.now = before.deadlineNanos();
            var late = f.runtime.confirmScenario(ack);
            f.run();
            assertFalse(late.join());
        }
    }

    @Test
    void waitsForTheLastFallAndNeverAdvancesTwiceFromTheSameTimer() throws Exception {
        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(true, false));
            f.run();
            var pending = f.runtime.snapshot().completion().orElseThrow();
            assertEquals(TimeUnit.MILLISECONDS.toNanos(1353), pending.settleNotBeforeNanos());
            var old = f.timers.commands.getFirst();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertEquals(MatchPhase.COMPLETION, f.runtime.snapshot().phase());
            assertEquals(1, f.owner.turns.size());
            var settle = f.timers.commands.getFirst();
            settle.run();
            f.run();
            assertEquals(MatchPhase.COMPLETION, f.runtime.snapshot().phase());
            f.timers.now = pending.settleNotBeforeNanos();
            settle.run();
            f.run();
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            old.run();
            settle.run();
            f.run();
            assertEquals(2, f.runtime.snapshot().turn().revision());
        }
    }

    @Test
    void excludesOnlyTheMissingRecipientAndEndsDeathAndInactivity() throws Exception {
        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.run();
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.DISCONNECTED, f.callbacks.reason);
            assertTrue(f.owner.closed.isEmpty());
            assertEquals(1, f.guest.closed.size());
        }

        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, true));
            f.run();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertEquals(RoomMatchEndReason.PARTICIPANT_ELIMINATED, f.callbacks.reason);
            assertEquals(1, f.owner.turns.size());
        }

        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(MatchPhase.SETTLING, f.runtime.snapshot().phase());
            assertEquals(null, f.callbacks.reason);
        }
    }

    @Test
    void preservesCommittedWorldAndLogsOneSafeFailureWhenNextTurnDeliveryFails() throws Exception {
        try (var fixture = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            var committed = f.runtime.snapshot().world();
            f.guest.turnDelivery = new CompletableFuture<>();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            f.guest.turnDelivery.completeExceptionally(new IllegalStateException("secret credential payload"));
            f.run();
            assertEquals(RoomMatchEndReason.TURN_DELIVERY_FAILED, f.callbacks.reason);
            assertSame(committed, f.runtime.snapshot().world());
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
            assertFalse(logs.text().contains("secret credential payload"));
        }

        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            f.timers.rejectSchedule = true;
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertEquals(RoomMatchEndReason.TURN_DELIVERY_FAILED, f.callbacks.reason);
        }
    }
}

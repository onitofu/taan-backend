package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.RoomMatchEndReason;

class ScenarioRuntimeTest {
    @Test
    void commitsOnceThroughTheOwnerAndPublishesOnlyCommittedRootsToObservers() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            var command = fixture.command();
            var first = f.runtime.applyScenario(command);
            var duplicate = f.runtime.applyScenario(command);
            assertFalse(first.isDone());
            assertEquals(MatchPhase.ACTION, before.phase());
            f.run();
            var result = first.join();
            var after = f.runtime.snapshot();
            var victim = after.participants().get(1).sessionId();

            assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, duplicate.join().outcome());
            assertSame(result.committed().orElseThrow(), duplicate.join().committed().orElseThrow());
            assertEquals(before.world().revision() + 1, after.world().revision());
            assertEquals(MatchPhase.COMPLETION, after.phase());
            assertEquals(923, after.actors().get(victim).hp());
            assertEquals(1000, before.actors().get(victim).hp());
            assertEquals(before.actors().get(victim).energy(), after.actors().get(victim).energy());
            assertTrue(result.committed().orElseThrow().changedCells() > 0);
            assertTrue(f.owner.scenarios.isEmpty());
            assertEquals(1, f.guest.scenarios.size());
            assertArrayEquals(command.root(), f.guest.scenarios.getFirst().scenario().root());
            assertEquals(1, f.owner.turns.size());
            assertEquals(1, f.guest.turns.size());
            assertTrue(logs.text().contains("match.scenario.applied"));
            assertTrue(logs.text().contains("worldRevision="));
            assertFalse(logs.text().contains("123456"));
            assertEquals(1, f.timers.deadlines.size());
            assertEquals(after.deadlineNanos(), f.timers.deadlines.getFirst().deadlineNanos());
            f.timers.now = after.deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.PREVIEW_TIMEOUT, f.callbacks.reason);
        }
    }

    @Test
    void rejectsAnotherRootAndRollsBackLateWorldFailuresBeforeCleanup() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var first = f.runtime.applyScenario(fixture.command());
            f.run();
            var before = f.runtime.snapshot();
            var changed = fixture.commands();
            changed.add(new ScenarioDamageCommand(new ScenarioCommandHeader(2, 4, 995, 0), 1, 1, 0, 0));
            var second = f.runtime.applyScenario(fixture.command(changed));
            f.run();

            assertEquals(ScenarioApplicationOutcome.APPLIED, first.join().outcome());
            assertEquals(ScenarioApplicationOutcome.REJECTED, second.join().outcome());
            assertSame(before.world(), f.runtime.snapshot().world());
            assertEquals(RoomMatchEndReason.INVALID_SCENARIO, f.callbacks.reason);
            assertEquals(1, f.guest.scenarios.size());
        }

        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            var commands = fixture.commands();
            commands.add(new ScenarioDamageCommand(new ScenarioCommandHeader(2, 4, 0, 0), 999, 1, 0, 0));
            int index = 2;
            var explosion = (ScenarioWeaponCommand) commands.get(index);
            var w = explosion.weapon();
            commands.set(
                index,
                new ScenarioWeaponCommand(
                    explosion.header(),
                    new ScenarioWeaponFields(
                        w.sequence(),
                        w.variant(),
                        new ScenarioIntPoint(Integer.MAX_VALUE, 0),
                        w.shotTick(),
                        w.scalar24Bits(),
                        w.scalar28()
                    )
                )
            );
            var result = f.runtime.applyScenario(fixture.command(commands));
            f.run();

            assertEquals(ScenarioApplicationOutcome.REJECTED, result.join().outcome());
            assertEquals("TERRAIN_COORDINATE_OVERFLOW", result.join().reason());
            assertSame(before.world(), f.runtime.snapshot().world());
            assertTrue(f.guest.scenarios.isEmpty());
        }
    }

    @Test
    void discardsStaleContextsRevisionsAndUnstartedOrStoppedWork() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var command = fixture.command();
            var before = f.runtime.snapshot();
            var c = command.context();
            var forged = new MatchCommandContext(
                c.matchId(),
                new RoomId(99),
                c.roomGeneration(),
                c.channelId(),
                c.sessionId(),
                c.admissionId()
            );
            var candidates = new ApplyScenarioCommand[]{
                new ApplyScenarioCommand(
                    forged,
                    command.turnRevision(),
                    command.worldRevision(),
                    command.claims(),
                    command.root()
                ),
                new ApplyScenarioCommand(
                    c,
                    command.turnRevision() + 1,
                    command.worldRevision(),
                    command.claims(),
                    command.root()
                ),
                new ApplyScenarioCommand(
                    c,
                    command.turnRevision(),
                    command.worldRevision() + 10,
                    command.claims(),
                    command.root()
                )
            };

            for (var candidate : candidates) {
                var result = f.runtime.applyScenario(candidate);
                f.run();
                assertEquals(ScenarioApplicationOutcome.IGNORED, result.join().outcome());
            }

            assertSame(before.world(), f.runtime.snapshot().world());
            var queued = f.runtime.applyScenario(command);
            f.runtime.stop();
            f.run();
            assertEquals(ScenarioApplicationOutcome.IGNORED, queued.join().outcome());
        }

        try (var fixture = new ScenarioRuntimeFixture(8, MatchExecutionMode.PREVIEW)) {
            var result = fixture.f.runtime.applyScenario(fixture.command());
            fixture.f.run();
            assertEquals(ScenarioApplicationOutcome.IGNORED, result.join().outcome());
            assertEquals(MatchPhase.PREVIEW, fixture.f.runtime.snapshot().phase());
        }
    }

    @Test
    void fencesCommitAfterValidationAndPreservesTheWorldOnUnexpectedFailure() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            var checks = new AtomicInteger();
            f.onAccess = () -> {
                if (checks.incrementAndGet() == 2) {
                    f.runtime.stop();
                }
            };
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            assertEquals("STALE_COMMIT", result.join().reason());
            assertSame(before.world(), f.runtime.snapshot().world());
            assertTrue(f.guest.scenarios.isEmpty());
        }

        try (var fixture = new ScenarioRuntimeFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            f.onAccess = () -> {
                throw new IllegalStateException("secret-password-verifier");
            };
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            assertEquals("APPLY_FAILED", result.join().reason());
            assertSame(before.world(), f.runtime.snapshot().world());
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
            assertFalse(logs.text().contains("secret-password-verifier"));
        }
    }

    @Test
    void preservesCommittedDamageOnDeliveryFailureAndIgnoresStaleFailureCallbacks() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var delivery = new CompletableFuture<Void>();
            f.guest.delivery = delivery;
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            var committed = result.join().committed().orElseThrow();
            delivery.completeExceptionally(new IllegalStateException("transport failed"));
            f.run();

            assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome());
            assertSame(committed.snapshot().world(), f.runtime.snapshot().world());
            assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, f.callbacks.reason);
            f.runtime.scenarioDeliveryFailed(committed, new IllegalStateException("late"));
            f.run();
        }

        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            f.timers.rejectSchedule = true;
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome());
            assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, f.callbacks.reason);
        }
    }

    @Test
    void cancelsTypedQueuedResultsWhenTheMailboxOverflows() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var queued = new ArrayList<CompletableFuture<ScenarioApplicationResult>>();

            for (int index = 0; index < 9; index++) {
                queued.add(fixture.f.runtime.applyScenario(fixture.command()));
            }

            fixture.f.run();
            queued.forEach(result -> assertEquals(ScenarioApplicationOutcome.IGNORED, result.join().outcome()));
        }
    }
}

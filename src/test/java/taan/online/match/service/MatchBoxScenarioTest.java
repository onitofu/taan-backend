package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.TestFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchCompletion;
import taan.online.match.model.MatchItemBox;
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.ScenarioCommitReceipt;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchBoxScenarioTest {
    @Test
    void acceptsTheUnchangedNativeMakeAndRelaysOnlyToTheObserverAfterAtomicWorldCommit() throws Exception {
        try (var boxes = new MatchBoxFixture(); var logs = new LogCapture()) {
            var f = boxes.f;
            byte[] root = MatchFixtures.root("native-cannon-0-0");
            var claims = new ScenarioClaimsMapper().map(new ScenarioCodec().decode(root));
            var before = f.runtime.snapshot();
            assertEquals(1, before.turn().boxAttemptBudget());
            assertEquals(1, f.owner.turns.getFirst().turn().boxAttemptBudget());
            var command = new ApplyScenarioCommand(
                f.context(),
                before.turn().revision(),
                before.world().revision(),
                claims,
                root
            );
            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(command).outcome());
            var after = f.runtime.snapshot();
            long id = 0x0500000000000001L;
            assertEquals(new MatchItemBox(id, 404, 644, true), after.world().boxes().get(id));
            assertEquals(before.world().revision() + 1, after.world().revision());
            assertTrue(before.world().boxes().isEmpty());
            assertTrue(f.owner.scenarios.isEmpty());
            assertEquals(1, f.guest.scenarios.size());
            assertArrayEquals(root, f.guest.scenarios.getFirst().scenario().root());
            assertThrows(UnsupportedOperationException.class, () -> after.world().boxes().clear());
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, boxes.apply(command).outcome());
            assertEquals(after, f.runtime.snapshot());
            assertEquals(1, logs.text().split("event=\"match.box.created\"", -1).length - 1);
            assertTrue(logs.text().contains("activeBoxCount=\"1\""));
            assertTrue(logs.text().contains("boxAttemptBudget=\"1\""));
            assertTrue(logs.text().contains("boxEntityId=\"" + id + "\""));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            boxes.acknowledge();
            var next = f.runtime.snapshot();
            assertEquals(1, next.turn().boxAttemptBudget());
            assertEquals(after.world().boxes(), next.world().boxes());
            assertTrue(boxes.pass());
            assertEquals(after.world().boxes(), f.runtime.snapshot().world().boxes());
        }
    }

    @Test
    void acceptsShotsWithoutSuccessfulCreationAndStopsAdvertisingAttemptsAtFiveActiveBoxes() throws Exception {
        try (var boxes = new MatchBoxFixture()) {
            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(boxes.command()).outcome());
            assertTrue(boxes.f.runtime.snapshot().world().boxes().isEmpty());
            boxes.acknowledge();

            for (int i = 1; i <= 5; i++) {
                assertEquals(1, boxes.f.runtime.snapshot().turn().boxAttemptBudget());
                var command = boxes.command(boxes.box(0x0500000000000000L | i, 784, i * 20, 200));
                assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(command).outcome());
                assertEquals(i, boxes.f.runtime.snapshot().world().boxes().size());
                boxes.acknowledge();
            }

            assertEquals(0, boxes.f.runtime.snapshot().turn().boxAttemptBudget());
            assertEquals(0, boxes.f.owner.turns.getLast().turn().boxAttemptBudget());
            assertEquals(0, boxes.f.guest.turns.getLast().turn().boxAttemptBudget());
            assertTrue(boxes.pass());
            assertEquals(5, boxes.f.runtime.snapshot().world().boxes().size());
            assertEquals(0, boxes.f.runtime.snapshot().turn().boxAttemptBudget());
            var before = boxes.f.runtime.snapshot();
            var extra = boxes.command(boxes.box(0x0500000000000006L, 784, 250, 200));
            var rejected = boxes.apply(extra);
            assertEquals(ScenarioApplicationOutcome.REJECTED, rejected.outcome());
            assertEquals("BOX_BUDGET_EXCEEDED", rejected.reason());
            assertEquals(before.world(), boxes.f.runtime.snapshot().world());
            assertTrue(boxes.f.match.world().boxes().isEmpty());
        }
    }

    @Test
    void rollsBackDamageTerrainAndTheFirstCreationWhenALaterMakeIsInvalid() throws Exception {
        for (boolean repeatedId : new boolean[]{false, true}) {
            try (var boxes = new MatchBoxFixture(); var logs = new LogCapture()) {
                var before = boxes.f.runtime.snapshot();
                long first = 0x0500000000000001L;
                var command = boxes.command(
                    boxes.box(first, 1100, 100, 200),
                    boxes.box(repeatedId ? first : first + 1, 1200, 200, 200)
                );
                var result = boxes.apply(command);
                assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                assertEquals(repeatedId ? "DUPLICATE_BOX_ID" : "BOX_BUDGET_EXCEEDED", result.reason());
                assertEquals(before.world(), boxes.f.runtime.snapshot().world());
                assertTrue(boxes.f.runtime.snapshot().world().boxes().isEmpty());
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, boxes.f.callbacks.reason);
                assertFalse(logs.text().contains("match.box.created"));
                assertTrue(logs.text().contains(result.reason()));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void rejectsReusedIdsAcrossTurnsAndIgnoresForeignAdmissionsWithoutTouchingTheRegistry() throws Exception {
        try (var boxes = new MatchBoxFixture()) {
            long id = 0x0500000000000001L;
            var first = boxes.command(boxes.box(id, 784, 200, 200));
            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(first).outcome());
            boxes.acknowledge();
            var command = boxes.command(boxes.box(id, 784, 400, 200));
            var c = command.context();
            var wrong = new MatchCommandContext(
                c.matchId(),
                c.roomId(),
                c.roomGeneration(),
                c.channelId() + 1,
                c.sessionId(),
                c.admissionId()
            );
            var foreign = new ApplyScenarioCommand(
                wrong,
                command.turnRevision(),
                command.worldRevision(),
                command.claims(),
                command.root()
            );
            var before = boxes.f.runtime.snapshot();
            assertEquals(ScenarioApplicationOutcome.IGNORED, boxes.apply(foreign).outcome());
            assertEquals(before, boxes.f.runtime.snapshot());
            assertEquals("DUPLICATE_BOX_ID", boxes.apply(command).reason());
            assertEquals(before.world(), boxes.f.runtime.snapshot().world());
        }
    }

    @Test
    void recalculatesTheNextBudgetFromActiveRecordsAndRetainsBoxesThroughPlacementAndEnergyChanges() throws Exception {
        try (var boxes = new MatchBoxFixture()) {
            var snapshot = boxes.f.runtime.snapshot();
            var registry = new HashMap<Long, MatchItemBox>();

            for (int i = 1; i <= 5; i++) {
                long id = 0x0500000000000000L | i;
                registry.put(id, new MatchItemBox(id, i * 20, 200, i != 1));
            }

            var original = snapshot.world();
            var member = snapshot.participants().getFirst();
            var world = new MatchWorldState(
                original.revision() + 1,
                original.actors(),
                original.terrain(),
                original.falls(),
                original.processedDeaths(),
                Optional.of(new ScenarioCommitReceipt(
                    "controlled-capacity-fixture",
                    member.sessionId(),
                    member.admissionId(),
                    snapshot.turn().revision(),
                    original.revision() + 1
                )),
                registry
            );
            var completion = new MatchCompletion(
                member.sessionId(),
                member.admissionId(),
                "controlled-capacity-fixture",
                snapshot.turn().revision(),
                world.revision(),
                Set.of(),
                0,
                Long.MAX_VALUE
            );
            assertTrue(boxes.f.match.commitScenario(
                original.revision(),
                snapshot.turn().revision(),
                world,
                Optional.of(completion)
            ));
            assertTrue(boxes.f.match.nextTurn(boxes.f.timers.now, 20_000_000_000L));
            assertEquals(1, boxes.f.match.turn().boxAttemptBudget());
            assertEquals(registry, world.grantEnergy(member, 1).boxes());
            assertEquals(registry, world.settled(member.sessionId(), member.spawn().settledY(), 1000).boxes());
            boxes.f.runtime.refresh();
            assertTrue(boxes.f.match.beginTermination());
            assertTrue(boxes.f.match.world().boxes().isEmpty());
            assertTrue(boxes.f.match.cancel());
            assertTrue(boxes.f.match.world().boxes().isEmpty());
        }
    }

    @Test
    void watchdogPreservesExistingBoxesAndDisconnectCleansTheRegistryBeforeAStaleTimerCanRun() throws Exception {
        try (var boxes = new MatchBoxFixture()) {
            var command = boxes.command(boxes.box(0x0500000000000001L, 784, 100, 200));
            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(command).outcome());
            boxes.acknowledge();
            var registry = boxes.f.runtime.snapshot().world().boxes();
            var timer = boxes.f.timers.commands.getFirst();
            boxes.f.timers.now = boxes.f.runtime.snapshot().deadlineNanos();
            timer.run();
            boxes.f.run();
            assertEquals(registry, boxes.f.runtime.snapshot().world().boxes());
            boxes.f.timers.now = boxes.f.match.settleAt();
            boxes.f.timers.commands.getFirst().run();
            boxes.f.run();
            assertEquals(registry, boxes.f.runtime.snapshot().world().boxes());
            assertEquals(1, boxes.f.runtime.snapshot().turn().boxAttemptBudget());
            var departure = new DepartMatchCommand(boxes.f.context(), RoomMatchEndReason.DISCONNECTED);
            var result = boxes.f.runtime.turnWork(() -> boxes.f.runtime.outcomes.depart(departure));
            boxes.f.run();
            assertTrue(result.join());
            assertFalse(boxes.f.runtime.running());
            assertTrue(boxes.f.match.world().boxes().isEmpty());
            timer.run();
            boxes.f.run();
            assertTrue(boxes.f.match.world().boxes().isEmpty());
        }
    }

    @Test
    void cancelsQueuedMakeAfterStopAndStartsARematchWithAnEmptyRegistry() throws Exception {
        try (var boxes = new MatchBoxFixture()) {
            var command = boxes.command(boxes.box(0x0500000000000001L, 784, 100, 200));
            var pending = boxes.f.runtime.applyScenario(command);
            boxes.f.runtime.stop();
            boxes.f.runtime.finish(() -> { });
            boxes.f.run();
            assertEquals(ScenarioApplicationOutcome.IGNORED, pending.join().outcome());
            assertTrue(boxes.f.match.world().boxes().isEmpty());
        }

        try (var fresh = new MatchBoxFixture()) {
            assertTrue(fresh.f.runtime.snapshot().world().boxes().isEmpty());
            assertEquals(1, fresh.f.runtime.snapshot().turn().boxAttemptBudget());
        }
    }
}

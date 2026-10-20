package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.TestFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.model.MatchItemSource;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchPickupScenarioTest {
    private static final long BOX = 0x0500000000000001L;

    @Test
    void commitsTheIndependentRootAndOneReceiptWithoutEchoAndRejectsDuplicateGrants() throws Exception {
        try (var pickup = new MatchPickupFixture(); var logs = new LogCapture()) {
            var f = pickup.f;
            var before = f.runtime.snapshot();
            byte[] root = MatchFixtures.root("projectile-pickup-power-slot0.synthetic");
            var command = new ApplyScenarioCommand(
                pickup.context(),
                before.turn().revision(),
                before.world().revision(),
                new ScenarioClaimsMapper().map(new ScenarioCodec().decode(root)),
                root
            );

            assertEquals(ScenarioApplicationOutcome.APPLIED, pickup.boxes.apply(command).outcome());
            var after = f.runtime.snapshot();
            var inventory = after.loot().inventory(command.context().sessionId());
            assertEquals(List.of(5, 65535, 65535), inventory.slots());
            assertEquals(1, inventory.receipts().size());
            var receipt = inventory.receipts().getFirst();
            assertEquals(MatchItemSource.PROJECTILE_PICKUP, receipt.source());
            assertEquals(command.context().admissionId(), receipt.admissionId());
            assertEquals(BOX, receipt.boxEntityId().orElseThrow());
            assertEquals(after.world().receipt().orElseThrow().fingerprint(), receipt.scenarioHash().orElseThrow());
            assertEquals(after.turn().revision(), receipt.turnRevision());
            assertFalse(after.world().boxes().get(BOX).active());
            assertEquals(5, after.world().boxes().get(BOX).boundItemId().orElseThrow());
            assertTrue(before.world().boxes().isEmpty());
            assertEquals(BattleInventory.empty(), before.loot().inventory(command.context().sessionId()));
            assertTrue(f.owner.scenarios.isEmpty());
            assertArrayEquals(root, f.guest.scenarios.getFirst().scenario().root());

            assertEquals(ScenarioApplicationOutcome.DUPLICATE, pickup.boxes.apply(command).outcome());
            assertSame(after, f.runtime.snapshot());

            assertTrue(pickup.confirm(0, 5));
            assertFalse(pickup.confirm(0, 5));
            assertEquals(1, f.runtime.snapshot().loot().grantSequence());
            assertTrue(logs.text().contains("event=\"match.box.picked_up\""));
            assertTrue(logs.text().contains("inventoryIndex=\"0\""));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains("PcGamePlayScenario"));

            pickup.boxes.acknowledge();
            assertEquals(1, f.runtime.snapshot().turn().boxAttemptBudget());
            assertEquals(after.world().boxes(), f.runtime.snapshot().world().boxes());
        }
    }

    @Test
    void usesEventOrderForTwoPickupsAndRequiresReceiptsInTheSameOrder() throws Exception {
        try (var pickup = new MatchPickupFixture()) {
            var boxes = pickup.boxes;
            var creation = boxes.command(boxes.box(BOX, 784, 100, 200));
            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(creation).outcome());

            boxes.acknowledge();
            var command = boxes.command(
                pickup.pickup(BOX, 780, 1, 0),
                boxes.box(BOX + 1, 784, 200, 200),
                pickup.pickup(BOX + 1, 785, 7, 285)
            );

            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(command).outcome());
            assertEquals(List.of(1, 7, 65535), pickup.slots());
            assertFalse(pickup.confirm(1, 7));
            assertTrue(pickup.confirm(0, 1));
            assertTrue(pickup.confirm(1, 7));
            assertEquals(2, pickup.f.runtime.snapshot().loot().grantSequence());
            assertTrue(pickup.f.runtime.snapshot().world().boxes().values().stream().noneMatch(b -> b.active()));
        }
    }

    @Test
    void fillsTheFirstHoleAndConsumesABoxWithoutAReceiptWhenAllThreeSlotsAreFull() throws Exception {
        for (boolean hole : new boolean[]{false, true}) {
            try (var pickup = new MatchPickupFixture(); var logs = new LogCapture()) {
                seedFullInventory(pickup);

                if (hole) {
                    assertTrue(pickup.discard(0, 5));
                }

                var command = pickup.boxes.command(
                    pickup.boxes.box(BOX, 784, 100, 200),
                    pickup.pickup(BOX, 785, 1005, 30)
                );
                assertEquals(ScenarioApplicationOutcome.APPLIED, pickup.boxes.apply(command).outcome());
                assertEquals(hole ? List.of(1005, 5, 5) : List.of(5, 5, 5), pickup.slots());
                assertEquals(hole ? 4 : 3, pickup.f.runtime.snapshot().loot().grantSequence());
                var inventory = pickup.f.runtime.snapshot().loot().inventory(pickup.context().sessionId());
                assertEquals(3, inventory.receipts().size());
                assertEquals(1005, pickup.f.runtime.snapshot().world().boxes().get(BOX).boundItemId().orElseThrow());
                assertEquals(hole, pickup.confirm(0, 1005));
                assertTrue(logs.text().contains(hole ? "INSERTED" : "INVENTORY_FULL"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void rollsBackEveryPreparedChangeAfterALaterInvalidPickup() throws Exception {
        for (boolean duplicate : new boolean[]{false, true}) {
            try (var pickup = new MatchPickupFixture(); var logs = new LogCapture()) {
                var before = pickup.f.runtime.snapshot();
                var command = pickup.boxes.command(
                    pickup.boxes.box(BOX, 784, 100, 200),
                    pickup.pickup(BOX, 1000, 5, 225),
                    pickup.pickup(duplicate ? BOX : BOX + 1, 1001, 7, 285)
                );
                var result = pickup.boxes.apply(command);

                assertEquals(ScenarioApplicationOutcome.REJECTED, result.outcome());
                assertEquals(duplicate ? "INACTIVE_BOX" : "UNKNOWN_BOX", result.reason());
                assertSame(before.world(), pickup.f.runtime.snapshot().world());
                assertSame(before.loot(), pickup.f.runtime.snapshot().loot());
                assertTrue(pickup.f.match.loot().inventories().isEmpty());
                assertFalse(logs.text().contains("match.box.picked_up"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }

        try (var pickup = new MatchPickupFixture()) {
            var before = pickup.f.runtime.snapshot();
            var command = pickup.boxes.command(
                pickup.pickup(BOX, 780, 5, 225),
                pickup.boxes.box(BOX, 784, 100, 200)
            );

            assertEquals("UNKNOWN_BOX", pickup.boxes.apply(command).reason());
            assertSame(before.world(), pickup.f.runtime.snapshot().world());
            assertSame(before.loot(), pickup.f.runtime.snapshot().loot());
        }
    }

    @Test
    void validatesEveryNativeCandidateAndKeepsTheOrdinaryDisabledPolicyClosed() throws Exception {
        int[] items = {1, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050, 5, 7};
        int[] draws = {0, 30, 63, 93, 120, 144, 165, 183, 198, 210, 219, 225, 285};

        for (int index = 0; index < items.length; index++) {
            try (var pickup = new MatchPickupFixture()) {
                var command = pickup.boxes.command(
                    pickup.boxes.box(BOX, 784, 100, 200),
                    pickup.pickup(BOX, 785, items[index], draws[index])
                );
                assertEquals(ScenarioApplicationOutcome.APPLIED, pickup.boxes.apply(command).outcome());
                assertEquals(items[index], pickup.slots().getFirst());
            }
        }

        try (var boxes = new MatchBoxFixture()) {
            var pickup = new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(0, 11, 785, 0), BOX, 5, 225);
            var command = boxes.command(boxes.box(BOX, 784, 100, 200), pickup);

            assertEquals("PICKUP_NOT_PERMITTED", boxes.apply(command).reason());
        }
    }

    @Test
    void refusesForeignRecipientsAndUncorrelatedConfirmationsAndPreservesTheShotBindingAcrossTurns() throws Exception {
        try (var pickup = new MatchPickupFixture()) {
            var command = pickup.boxes.command(
                pickup.boxes.box(BOX, 784, 100, 200),
                pickup.pickup(BOX, 785, 5, 225)
            );
            assertEquals(ScenarioApplicationOutcome.APPLIED, pickup.boxes.apply(command).outcome());
            var context = pickup.context();
            var stale = new MatchCommandContext(
                context.matchId(),
                context.roomId(),
                context.roomGeneration(),
                context.channelId(),
                context.sessionId(),
                UUID.randomUUID()
            );
            var confirmation = MatchItemExecution.confirmItemReceipt(
                pickup.f.runtime,
                new ConfirmMatchItemReceiptCommand(stale, 0, 0, 5)
            );
            pickup.f.run();
            assertFalse(confirmation.toCompletableFuture().join());
            var receipt = pickup.f.runtime.snapshot().loot().inventory(context.sessionId()).receipts().getFirst();
            assertFalse(receipt.confirmed());

            pickup.boxes.acknowledge();
            var again = pickup.boxes.command(pickup.pickup(BOX, 785, 5, 225));
            assertEquals("INACTIVE_BOX", pickup.boxes.apply(again).reason());
        }

        try (var pickup = new MatchPickupFixture()) {
            var foreign = new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(2, 11, 785, 0), BOX, 5, 225);
            var command = pickup.boxes.command(pickup.boxes.box(BOX, 784, 100, 200), foreign);

            assertEquals("INVALID_COMMAND_OWNER", pickup.boxes.apply(command).reason());
        }
    }

    @Test
    void atomicallyRemovesDeadRecipientsAndIgnoresTheirLateNotifyWithoutDisconnecting() throws Exception {
        try (var pickup = new MatchPickupFixture()) {
            var command = pickup.boxes.command(
                pickup.boxes.box(BOX, 784, 100, 200),
                pickup.pickup(BOX, 785, 5, 225),
                new ScenarioBaseCommand(new ScenarioCommandHeader(0, 5, 1100, 0))
            );
            assertEquals(ScenarioApplicationOutcome.APPLIED, pickup.boxes.apply(command).outcome());
            assertTrue(pickup.f.runtime.snapshot().actors().get(pickup.context().sessionId()).dead());
            var inventory = pickup.f.runtime.snapshot().loot().inventory(pickup.context().sessionId());
            assertEquals(BattleInventory.empty(), inventory);
            assertTrue(pickup.confirm(0, 5));
            assertTrue(pickup.f.runtime.running());
            assertEquals(1, pickup.f.runtime.snapshot().loot().grantSequence());
        }
    }

    @Test
    void rejectsAnObsoleteLootBaseAndClearsAllTemporaryStateAtDisconnectAndRematch() throws Exception {
        try (var pickup = new MatchPickupFixture()) {
            var before = pickup.f.runtime.snapshot();
            var command = pickup.boxes.command(
                pickup.boxes.box(BOX, 784, 100, 200),
                pickup.pickup(BOX, 785, 5, 225)
            );
            var accepted = new ValidateScenarioAction((context, member) -> true).execute(
                new ScenarioAdmissionContext(before, command.context(), command.turnRevision(), Optional.empty()),
                command.claims(),
                command.root()
            ).accepted().orElseThrow();
            var prepared = new PrepareScenarioAction().execute(before, accepted, MatchPickupFixture.POLICY);
            var publication = before.completed(prepared.world(), prepared.loot(), Optional.empty());
            seedFullInventory(pickup);

            assertFalse(pickup.f.runtime.commitScenario(command, prepared, publication));
            assertSame(before.world(), pickup.f.match.world());
            assertNotEquals(before.loot(), pickup.f.match.loot());

            var departure = new DepartMatchCommand(pickup.context(), RoomMatchEndReason.DISCONNECTED);
            var pending = pickup.f.runtime.turnWork(() -> pickup.f.runtime.outcomes.depart(departure));
            pickup.f.run();
            assertTrue(pending.join());
            assertTrue(pickup.f.match.loot().inventories().isEmpty());
            assertTrue(pickup.f.match.world().boxes().isEmpty());
        }

        try (var fresh = new MatchPickupFixture()) {
            assertEquals(0, fresh.f.runtime.snapshot().loot().grantSequence());
            assertTrue(fresh.f.runtime.snapshot().loot().inventories().isEmpty());
        }
    }

    @Test
    void rejectsAPickupWithoutItsProjectileOrWithAnInconsistentProducerDraw() throws Exception {
        for (int tick : new int[]{600, 785}) {
            try (var pickup = new MatchPickupFixture()) {
                var command = pickup.boxes.command(
                    pickup.boxes.box(BOX, 500, 100, 200),
                    pickup.pickup(BOX, tick, 5, 0)
                );
                assertEquals(
                    tick == 600 ? "MISSING_PROJECTILE" : "INVALID_PICKUP_REWARD",
                    pickup.boxes.apply(command).reason()
                );
            }
        }
    }

    @Test
    void ignoresForeignChannelAndAdmissionAndCancelsQueuedPickupWhenTheRuntimeStops() throws Exception {
        try (var pickup = new MatchPickupFixture()) {
            var before = pickup.f.runtime.snapshot();
            var command = pickup.boxes.command(
                pickup.boxes.box(BOX, 784, 100, 200),
                pickup.pickup(BOX, 785, 5, 225)
            );
            var c = command.context();

            for (boolean channel : new boolean[]{false, true}) {
                var wrong = new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    channel ? c.channelId() + 1 : c.channelId(),
                    c.sessionId(),
                    channel ? c.admissionId() : UUID.randomUUID()
                );
                var foreign = new ApplyScenarioCommand(
                    wrong,
                    command.turnRevision(),
                    command.worldRevision(),
                    command.claims(),
                    command.root()
                );
                assertEquals(ScenarioApplicationOutcome.IGNORED, pickup.boxes.apply(foreign).outcome());
                assertSame(before, pickup.f.runtime.snapshot());
            }

            var timer = pickup.f.timers.commands.getFirst();
            var pending = pickup.f.runtime.applyScenario(command);
            pickup.f.runtime.stop();
            pickup.f.runtime.finish(() -> { });
            pickup.f.run();
            assertEquals(ScenarioApplicationOutcome.IGNORED, pending.join().outcome());
            timer.run();
            pickup.f.run();
            assertTrue(pickup.f.match.loot().inventories().isEmpty());
            assertTrue(pickup.f.match.world().boxes().isEmpty());
        }
    }

    private void seedFullInventory(MatchPickupFixture pickup) {
        var context = pickup.context();
        var loot = pickup.f.match.loot();

        for (int turn = 1; turn <= 3; turn++) {
            loot = loot.offered(new MatchBonusOffer(context.sessionId(), context.admissionId(), turn, 0, false))
                .granted(context.sessionId(), 5).confirmed(context.sessionId(), context.admissionId(), turn - 1, 5);
        }

        pickup.f.match.updateLoot(loot);
        pickup.f.runtime.refresh();
    }
}

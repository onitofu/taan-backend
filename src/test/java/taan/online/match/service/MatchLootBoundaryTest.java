package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchStateTestAccess;
import taan.online.TestFixtures;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchLootState;
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.ScenarioCommitReceipt;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchLootBoundaryTest {
    @Test
    void rejectsUnownedUnknownGaugeStaleAndUnconfirmedReportsWithoutEchoOrDisconnection() throws Exception {
        try (var loot = new MatchLootFixture(); var logs = new LogCapture()) {
            var f = loot.f;
            loot.offerOwner();
            assertFalse(loot.report(0, 2));
            var source = loot.bonus(0, 5);
            var gauged = new ReportMatchBonusCommand(source.context(), 0, 0, OptionalInt.of(0), -1, 5, 0, 0, 0);
            assertFalse(loot.complete(MatchItemExecution.reportBonus(f.runtime, gauged)));
            var offer = f.match.loot().offers().get(f.room.owner);
            f.match.updateLoot(f.match.loot().offered(new MatchBonusOffer(
                f.room.owner,
                UUID.randomUUID(),
                offer.turnRevision(),
                0,
                false
            )));
            assertFalse(loot.report(0, 5));
            f.match.updateLoot(f.match.loot().offered(new MatchBonusOffer(
                f.room.owner,
                offer.admissionId(),
                offer.turnRevision(),
                1,
                false
            )));
            f.runtime.refresh();
            assertEquals(0, f.runtime.snapshot().turn().pickerBase());
            assertFalse(loot.report(0, 5));
            f.match.updateLoot(f.match.loot().offered(offer));
            f.runtime.refresh();

            for (int index : new int[]{-1, 3}) {
                assertFalse(loot.confirm(0, index, 5));
                assertFalse(loot.discard(0, index, 5));
            }

            var foreignSlot = new ConfirmMatchItemReceiptCommand(loot.context(0), 2, 0, 5);
            assertFalse(loot.complete(MatchItemExecution.confirmItemReceipt(f.runtime, foreignSlot)));
            assertFalse(loot.complete(MatchItemExecution.discardItem(
                f.runtime,
                new DiscardMatchItemCommand(loot.context(0), 2, 0, 5)
            )));
            var wrong = new MatchCommandContext(
                source.context().matchId(),
                source.context().roomId(),
                source.context().roomGeneration(),
                source.context().channelId() + 1,
                source.context().sessionId(),
                source.context().admissionId()
            );
            assertFalse(loot.complete(MatchItemExecution.reportBonus(
                f.runtime,
                new ReportMatchBonusCommand(wrong, 0, 0, OptionalInt.empty(), -1, 5, 0, 0, 0)
            )));
            assertTrue(f.owner.bonuses.isEmpty());
            assertTrue(f.owner.closed.isEmpty());
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains("itemId"));
            assertTrue(logs.text().contains("INVALID_BONUS_OFFER"));
        }
    }

    @Test
    void bindsQueuedWorkToTheCurrentAdmissionAndRemovesAllLootOnDepartureAndTermination() throws Exception {
        try (var loot = new MatchLootFixture()) {
            var f = loot.f;
            loot.offerOwner();
            var queued = MatchItemExecution.reportBonus(f.runtime, loot.bonus(0, 5));
            f.admitted = false;
            assertFalse(loot.complete(queued));
            assertTrue(f.owner.bonuses.isEmpty());
            f.admitted = true;
            assertTrue(loot.report(0, 5));
            var stale = MatchItemExecution.confirmItemReceipt(
                f.runtime,
                new ConfirmMatchItemReceiptCommand(loot.context(0), 0, 0, 5)
            );
            f.admitted = false;
            assertFalse(loot.complete(stale));
            f.admitted = true;
            assertTrue(loot.confirm(0, 0, 5));
            f.match.withdraw(f.room.owner, MatchExitKind.DEPARTED);
            f.runtime.refresh();
            assertFalse(f.match.loot().inventories().containsKey(f.room.owner));
            assertFalse(f.match.loot().offers().containsKey(f.room.owner));
            assertFalse(loot.report(0, 5));
            assertFalse(loot.confirm(0, 0, 5));
            assertFalse(loot.discard(0, 0, 5));
        }

        try (var loot = new MatchLootFixture()) {
            loot.offerOwner();
            assertTrue(loot.report(0, 5));
            var queued = MatchItemExecution.discardItem(
                loot.f.runtime,
                new DiscardMatchItemCommand(loot.context(0), 0, 0, 5)
            );
            loot.f.runtime.stop();
            loot.f.runtime.finish(() -> { });
            loot.f.run();
            assertFalse(queued.toCompletableFuture().join());
            assertEquals(MatchLootState.empty(), loot.f.match.loot());
        }

        try (var loot = new MatchLootFixture()) {
            assertEquals(MatchLootState.empty(), loot.f.match.loot());
        }
    }

    @Test
    void handlesBonusDuringCompletionAndFallingAndDoesNotIssueAnOfferToADeadOrDepartedActor() throws Exception {
        try (var loot = new MatchLootFixture()) {
            loot.offerOwner();
            loot.pass();
            loot.f.runtime.applyScenario(loot.movement.live.shot(false, false));
            loot.f.run();
            assertTrue(loot.report(2, 7));
            loot.f.runtime.confirmScenario(loot.movement.live.ack(0, false));
            loot.f.runtime.confirmScenario(loot.movement.live.ack(1, false));
            loot.f.run();
            assertTrue(loot.report(0, 5));
        }

        try (var loot = new MatchLootFixture()) {
            assertTrue(loot.movement.move(loot.movement.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(loot.movement.pass(loot.movement.pass()));
            assertFalse(loot.report(0, 5));
            loot.movement.timer(loot.f.match.settleAt());
            assertTrue(loot.report(0, 5));
        }

        try (var loot = new MatchLootFixture()) {
            var f = loot.f;
            loot.offerOwner();
            assertTrue(loot.report(0, 5));
            assertTrue(loot.confirm(0, 0, 5));
            f.runtime.applyScenario(loot.movement.live.shot(false, true));
            f.run();
            assertTrue(f.runtime.snapshot().actors().get(f.room.owner).dead());
            assertFalse(loot.discard(0, 0, 5));
        }
    }

    @Test
    void fencesFailedDeliveryAndSkipsARecipientWhoseAdmissionExpiredBeforePublication() throws Exception {
        for (boolean asynchronous : new boolean[]{false, true}) {
            try (var loot = new MatchLootFixture(); var logs = new LogCapture()) {
                var f = loot.f;
                loot.offerOwner();

                if (asynchronous) {
                    f.owner.delivery = new CompletableFuture<>();
                } else {
                    f.owner.motionFailure = new IllegalStateException(TestFixtures.CREDENTIAL);
                }

                assertTrue(loot.report(0, 5));

                if (asynchronous) {
                    f.owner.delivery.completeExceptionally(new IllegalStateException(TestFixtures.CREDENTIAL));
                    f.run();
                }

                assertFalse(f.runtime.running());
                assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, f.callbacks.reason);
                assertEquals(MatchLootState.empty(), f.match.loot());
                assertEquals(1, logs.text().split("event=\"match.item.delivery.failed\"", -1).length - 1);
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }

        try (var loot = new MatchLootFixture()) {
            loot.offerOwner();
            loot.f.owner.onMovement = () -> loot.f.admitted = false;
            assertTrue(loot.report(0, 5));
            assertEquals(1, loot.f.owner.bonuses.size());
            assertEquals(0, loot.f.guest.bonuses.size());
        }
    }

    @Test
    void keepsOffersDisabledInPreviewAndForStoppedOrUnadmittedOwners() throws Exception {
        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.PREVIEW, new MatchLootPolicy(Set.of(5)))) {
            f.runtime.items.loot.offerBonus();
            assertTrue(f.match.loot().offers().isEmpty());
        }

        try (var f = new MatchRuntimeFixture(
            8,
            MatchExecutionMode.CLIENT_SCENARIO,
            new MatchLootPolicy(Set.of(5)),
            RoomMode.NO_ITEM
        )) {
            f.runtime.items.loot.offerBonus();
            assertTrue(f.match.loot().offers().isEmpty());
        }

        try (var loot = new MatchLootFixture()) {
            loot.f.runtime.items.loot.offerBonus();
            var before = loot.f.match.loot();
            loot.f.runtime.items.loot.offerBonus();
            assertEquals(before, loot.f.match.loot());
        }

        try (var loot = new MatchLootFixture()) {
            loot.f.admitted = false;
            loot.f.runtime.items.loot.offerBonus();
            assertTrue(loot.f.match.loot().offers().isEmpty());
            loot.f.admitted = true;
            loot.f.runtime.stop();
            loot.f.runtime.items.loot.offerBonus();
            assertTrue(loot.f.match.loot().offers().isEmpty());
        }
    }

    @Test
    void refusesAnUnusedBonusAfterDeathAndDoesNotOfferAnotherBonusToTheDeadActiveActor() throws Exception {
        try (var loot = new MatchLootFixture()) {
            loot.offerOwner();
            loot.f.runtime.applyScenario(loot.movement.live.shot(false, true));
            loot.f.run();
            assertTrue(loot.f.runtime.snapshot().actors().get(loot.f.room.owner).dead());
            assertFalse(loot.report(0, 5));
        }

        try (var loot = new MatchLootFixture()) {
            var f = loot.f;
            var snapshot = f.runtime.snapshot();
            var member = snapshot.participants().getFirst();
            var world = snapshot.world().settled(member.sessionId(), snapshot.definitions().map().height() + 500, 0);
            var prepared = new MatchWorldState(
                world.revision(),
                world.actors(),
                world.terrain(),
                world.falls(),
                world.processedDeaths(),
                Optional.of(new ScenarioCommitReceipt(
                    "dead-actor-fixture",
                    member.sessionId(),
                    member.admissionId(),
                    snapshot.turn().revision(),
                    world.revision()
                ))
            );
            assertTrue(MatchStateTestAccess.commit(
                f.match,
                snapshot.world().revision(),
                snapshot.turn().revision(),
                prepared
            ));
            f.runtime.refresh();
            f.runtime.items.loot.offerBonus();
            assertTrue(f.match.loot().offers().isEmpty());
        }
    }

}

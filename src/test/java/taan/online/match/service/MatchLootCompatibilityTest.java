package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchExitKind;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.ReportMatchPickupCommand;

class MatchLootCompatibilityTest {
    @Test
    void consumesValidMessagesAndRejectsForeignSlotsWithoutPublishingOrChangingState() throws Exception {
        try (var live = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = live.f;
            var context = f.context();
            var before = f.runtime.snapshot();

            assertTrue(pickup(f, context, 0, 0, OptionalInt.empty(), 123));
            assertTrue(pickup(f, context, 0, 0, OptionalInt.of(0), 123));
            assertFalse(pickup(f, context, 2, 2, OptionalInt.empty(), 123));
            assertFalse(pickup(f, context, 0, 2, OptionalInt.empty(), 123));
            assertFalse(pickup(f, context, 0, 0, OptionalInt.of(2), 123));
            assertFalse(pickup(f, context, 0, 0, OptionalInt.empty(), -1));

            for (int index : new int[]{0, 1, 2}) {
                assertTrue(receipt(f, context, 0, index));
                assertTrue(discard(f, context, 0, index));
            }

            assertFalse(receipt(f, context, 2, 0));
            assertFalse(discard(f, context, 2, 0));

            for (int index : new int[]{-1, 3}) {
                assertFalse(receipt(f, context, 0, index));
                assertFalse(discard(f, context, 0, index));
            }

            assertEquals(before, f.runtime.snapshot());
            assertTrue(f.owner.specials.isEmpty());
            assertTrue(f.guest.specials.isEmpty());
            assertTrue(f.owner.spends.isEmpty());
            assertTrue(f.guest.spends.isEmpty());
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(f.guest.closed.isEmpty());
            assertTrue(logs.text().contains("match.pickup.ignored"));
            assertTrue(logs.text().contains("match.item.receipt.ignored"));
            assertTrue(logs.text().contains("match.item.discard.ignored"));
            assertTrue(logs.text().contains("UNSUPPORTED_PICKUP"));
            assertTrue(logs.text().contains("FEATURE_DISABLED"));
            assertTrue(logs.text().contains("INVALID_ITEM_CONTEXT"));
            assertFalse(logs.text().contains("itemId"));
            assertFalse(logs.text().contains("opaque24"));
        }
    }

    @Test
    void revalidatesAdmissionChannelMatchAndDepartedSessionsWhenQueuedWorkRuns() throws Exception {
        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var c = f.context();
            var before = f.runtime.snapshot();
            var foreignChannel = new MatchCommandContext(
                c.matchId(),
                c.roomId(),
                c.roomGeneration(),
                c.channelId() + 1,
                c.sessionId(),
                c.admissionId()
            );
            var staleAdmission = new MatchCommandContext(
                c.matchId(),
                c.roomId(),
                c.roomGeneration(),
                c.channelId(),
                c.sessionId(),
                UUID.randomUUID()
            );
            var foreignGeneration = new MatchCommandContext(
                c.matchId(),
                c.roomId(),
                UUID.randomUUID(),
                c.channelId(),
                c.sessionId(),
                c.admissionId()
            );

            for (var context : new MatchCommandContext[]{foreignChannel, staleAdmission, foreignGeneration}) {
                assertFalse(pickup(f, context, 0, 0, OptionalInt.empty(), 123));
                assertFalse(receipt(f, context, 0, 0));
                assertFalse(discard(f, context, 0, 0));
            }

            var queued = MatchItemExecution.confirmItemReceipt(
                f.runtime,
                new ConfirmMatchItemReceiptCommand(c, 0, 0, 5)
            );
            f.admitted = false;
            f.run();
            assertFalse(queued.toCompletableFuture().join());
            assertEquals(before, f.runtime.snapshot());

            f.admitted = true;
            f.match.withdraw(c.sessionId(), MatchExitKind.DEPARTED);
            f.runtime.refresh();
            assertFalse(pickup(f, c, 0, 0, OptionalInt.empty(), 123));
            assertFalse(receipt(f, c, 0, 0));
            assertFalse(discard(f, c, 0, 0));
        }
    }

    @Test
    void ignoresPreviewUnstartedMissingAndStoppedMatches() throws Exception {
        for (var mode : new MatchExecutionMode[]{MatchExecutionMode.PREVIEW, MatchExecutionMode.CANNON_1V1}) {
            try (var f = new MatchRuntimeFixture(8, mode)) {
                var c = f.context();
                assertFalse(pickup(f, c, 0, 0, OptionalInt.empty(), 123));
                assertFalse(receipt(f, c, 0, 0));
                assertFalse(discard(f, c, 0, 0));

                var pickup = new ReportMatchPickupCommand(c, 0, 0, OptionalInt.empty(), 123, 5);
                var receipt = new ConfirmMatchItemReceiptCommand(c, 0, 0, 5);
                var discard = new DiscardMatchItemCommand(c, 0, 0, 5);
                assertFalse(MatchItemExecution.reportPickup(null, pickup).toCompletableFuture().join());
                assertFalse(MatchItemExecution.confirmItemReceipt(null, receipt).toCompletableFuture().join());
                assertFalse(MatchItemExecution.discardItem(null, discard).toCompletableFuture().join());
                assertFalse(new ReportMatchPickupAction(f.room.scenario.matches).execute(pickup)
                    .toCompletableFuture().join());
                assertFalse(new ConfirmMatchItemReceiptAction(f.room.scenario.matches).execute(receipt)
                    .toCompletableFuture().join());
                assertFalse(new DiscardMatchItemAction(f.room.scenario.matches).execute(discard)
                    .toCompletableFuture().join());
            }
        }

        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var queued = MatchItemExecution.discardItem(
                f.runtime,
                new DiscardMatchItemCommand(f.context(), 0, 0, 5)
            );
            f.runtime.stop();
            f.run();
            assertFalse(queued.toCompletableFuture().join());
        }
    }

    private boolean pickup(
        MatchRuntimeFixture f,
        MatchCommandContext context,
        int slot,
        int nested,
        OptionalInt gauge,
        long entity
    ) {
        var result = MatchItemExecution.reportPickup(
            f.runtime,
            new ReportMatchPickupCommand(context, slot, nested, gauge, entity, 5)
        );
        return complete(f, result);
    }

    private boolean receipt(MatchRuntimeFixture f, MatchCommandContext context, int slot, int index) {
        var result = MatchItemExecution.confirmItemReceipt(
            f.runtime,
            new ConfirmMatchItemReceiptCommand(context, slot, index, 5)
        );
        return complete(f, result);
    }

    private boolean discard(MatchRuntimeFixture f, MatchCommandContext context, int slot, int index) {
        var result = MatchItemExecution.discardItem(f.runtime, new DiscardMatchItemCommand(context, slot, index, 5));
        return complete(f, result);
    }

    private boolean complete(MatchRuntimeFixture f, CompletionStage<Boolean> result) {
        f.run();
        return result.toCompletableFuture().join();
    }
}

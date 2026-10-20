package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchBonusDelivery;

class MatchLootFlowTest {
    @Test
    void grantsOnceAfterTheNextTurnStartAndWaitsForTheOrderedOwnerReceiptBeforeDiscard() throws Exception {
        try (var loot = new MatchLootFixture(); var logs = new LogCapture()) {
            var f = loot.f;
            var initial = f.runtime.snapshot();
            assertFalse(loot.report(0, 5));
            assertTrue(initial.loot().offers().isEmpty());
            loot.offerOwner();
            assertEquals(2, f.runtime.snapshot().turn().activeSlot());
            var beforeGrant = f.runtime.snapshot();
            assertTrue(loot.report(0, 5));
            var granted = f.runtime.snapshot();
            var inventory = granted.loot().inventory(f.room.owner);
            assertEquals(List.of(5, 65535, 65535), inventory.slots());
            assertEquals(1, inventory.receipts().size());
            assertEquals(1, inventory.receipts().getFirst().turnRevision());
            assertEquals(beforeGrant.world(), granted.world());
            var expected = new MatchBonusDelivery(0, -17, -3, 5, -1);
            assertEquals(List.of(expected), f.owner.bonuses);
            assertEquals(f.owner.bonuses, f.guest.bonuses);
            assertFalse(loot.report(0, 5));
            assertFalse(loot.discard(0, 0, 5));
            assertFalse(loot.confirm(2, 0, 5));
            assertFalse(loot.confirm(0, 1, 5));
            assertFalse(loot.confirm(0, 0, 7));

            loot.pass();
            assertTrue(f.runtime.snapshot().loot().offers().get(f.room.owner) == null);
            assertTrue(loot.confirm(0, 0, 5));
            assertFalse(loot.confirm(0, 0, 5));
            assertFalse(loot.discard(0, 0, 7));
            assertTrue(loot.discard(0, 0, 5));
            assertFalse(loot.discard(0, 0, 5));
            assertEquals(1, f.owner.discards.size());
            assertEquals(f.owner.discards, f.guest.discards);
            assertEquals(List.of(65535, 65535, 65535), f.runtime.snapshot().loot().inventory(f.room.owner).slots());
            assertEquals(List.of(5, 65535, 65535), granted.loot().inventory(f.room.owner).slots());
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(logs.text().contains("match.bonus.granted"));
            assertTrue(logs.text().contains("match.item.receipt.confirmed"));
            assertTrue(logs.text().contains("match.item.discarded"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains("itemId"));
            assertFalse(logs.text().contains("opaque24"));
        }
    }

    @Test
    void fillsThreeSlotsKeepsFullGrantsReceiptFreeAndReusesTheFirstConfirmedHole() throws Exception {
        try (var loot = new MatchLootFixture()) {
            var f = loot.f;

            for (int item : new int[]{5, 7, 1}) {
                loot.offerOwner();
                assertTrue(loot.report(0, item));
            }

            assertFalse(loot.confirm(0, 1, 7));
            assertFalse(loot.confirm(0, 2, 1));
            assertTrue(loot.confirm(0, 0, 5));
            assertTrue(loot.confirm(0, 1, 7));
            assertTrue(loot.confirm(0, 2, 1));
            var beforeFull = f.runtime.snapshot().loot().inventory(f.room.owner);
            loot.offerOwner();
            assertTrue(loot.report(0, 1005));
            assertFalse(loot.report(0, 1005));
            assertEquals(beforeFull, f.runtime.snapshot().loot().inventory(f.room.owner));
            assertEquals(3, f.runtime.snapshot().loot().grantSequence());
            assertFalse(loot.confirm(0, 0, 1005));
            assertEquals(4, f.owner.bonuses.size());

            assertTrue(loot.discard(0, 1, 7));
            loot.offerOwner();
            assertTrue(loot.report(0, 1005));
            assertEquals(List.of(5, 1005, 1), f.runtime.snapshot().loot().inventory(f.room.owner).slots());
            assertFalse(loot.confirm(0, 1, 7));
            assertFalse(loot.discard(0, 1, 7));
            assertTrue(loot.confirm(0, 1, 1005));
            assertEquals(5, f.owner.bonuses.size());
            assertEquals(f.owner.bonuses, f.guest.bonuses);
        }
    }

    @Test
    void expiresAnUnusedOfferBeforeTheNextOwnTurnAndPreservesTheScalarAtIssueTime() throws Exception {
        try (var loot = new MatchLootFixture()) {
            var f = loot.f;
            loot.offerOwner();
            var previous = f.runtime.snapshot().loot().offers().get(f.room.owner);
            assertEquals(0, previous.pickerScalar());
            assertFalse(previous.consumed());
            loot.pass();
            assertFalse(loot.report(0, 5));
            assertEquals(0, f.owner.bonuses.size());
            assertTrue(f.runtime.snapshot().loot().offers().containsKey(f.room.players.get(1)));
            loot.pass();
            var next = f.runtime.snapshot().loot().offers().get(f.room.owner);
            assertEquals(previous.turnRevision() + 2, next.turnRevision());
            assertTrue(loot.report(0, 5));
        }
    }

    @Test
    void opensTheWatchdogOfferBeforeTurnEndAndDoesNotReopenItWhenSettlementAdvances() throws Exception {
        try (var loot = new MatchLootFixture()) {
            var f = loot.f;
            f.owner.onMovement = () -> assertTrue(f.runtime.snapshot().loot().offers().containsKey(f.room.owner));
            loot.movement.timer(f.runtime.snapshot().deadlineNanos());
            assertEquals(MatchPhase.SETTLING, f.runtime.snapshot().phase());
            assertEquals(List.of(0), f.owner.stops);
            assertTrue(loot.report(0, 5));
            loot.movement.timer(f.match.settleAt());
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertFalse(loot.report(0, 5));
            assertEquals(1, f.owner.bonuses.size());
        }
    }
}

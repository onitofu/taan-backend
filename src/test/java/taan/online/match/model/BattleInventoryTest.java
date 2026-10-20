package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BattleInventoryTest {
    private static final UUID ADMISSION = UUID.randomUUID();

    @Test
    void insertsIntoTheFirstHoleAndConfirmsInGrantOrderBeforeAllowingDiscard() {
        var empty = BattleInventory.empty();
        var first = empty.inserted(receipt(1, 0, 5));
        var second = first.inserted(receipt(2, 1, 7));
        assertSame(second, second.confirmed(UUID.randomUUID(), 0, 5));
        assertSame(second, second.confirmed(ADMISSION, 1, 7));
        assertSame(second, second.confirmed(ADMISSION, 0, 7));
        assertSame(second, second.discarded(0, 5));
        assertSame(second, second.discarded(2, 5));

        var confirmed = second.confirmed(ADMISSION, 0, 5).confirmed(ADMISSION, 1, 7);
        assertSame(confirmed, confirmed.confirmed(ADMISSION, 1, 7));
        assertSame(confirmed, confirmed.discarded(0, 7));
        var hole = confirmed.discarded(0, 5);
        assertEquals(0, hole.firstEmpty());
        var replacement = hole.inserted(receipt(3, 0, 1005));
        assertEquals(List.of(1005, 7, 65535), replacement.slots());
        assertEquals(List.of(2L, 3L), replacement.receipts().stream().map(MatchItemReceipt::grantSequence).toList());
        assertThrows(UnsupportedOperationException.class, () -> replacement.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> replacement.receipts().clear());
        assertEquals(List.of(65535, 65535, 65535), empty.slots());
    }

    @Test
    void refusesInvalidCapacityOutOfOrderInsertionAndInsertionIntoAFullInventory() {
        var first = receipt(1, 0, 5);
        assertThrows(IllegalArgumentException.class, () -> new BattleInventory(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BattleInventory(
            List.of(5, 5, 5),
            List.of(first, first, first, first)
        ));
        assertThrows(IllegalArgumentException.class, () -> BattleInventory.empty().inserted(receipt(1, 1, 5)));
        var full = BattleInventory.empty().inserted(first).inserted(receipt(2, 1, 7)).inserted(receipt(3, 2, 1005));
        assertEquals(-1, full.firstEmpty());
        assertThrows(IllegalArgumentException.class, () -> full.inserted(receipt(4, -1, 5)));
        assertThrows(IllegalArgumentException.class, () -> full.inserted(receipt(4, 0, 5)));
    }

    @Test
    void consumesOneOfferEvenWhenFullAndKeepsSnapshotsIndependentOfLaterOffersAndCleanup() {
        var session = UUID.randomUUID();
        var admission = UUID.randomUUID();
        var initial = MatchLootState.empty();
        assertSame(initial, initial.granted(session, 5));
        assertSame(initial, initial.expired(session));
        assertSame(initial, initial.without(session));
        assertSame(BattleInventory.empty(), initial.inventory(session));
        assertSame(initial, initial.confirmed(session, admission, 0, 5));
        assertSame(initial, initial.discarded(session, 0, 5));
        var state = initial;

        for (int turn = 1; turn <= 4; turn++) {
            state = state.offered(new MatchBonusOffer(session, admission, turn, 0, false)).granted(session, 5);
            assertSame(state, state.granted(session, 5));
        }

        assertEquals(List.of(5, 5, 5), state.inventory(session).slots());
        assertEquals(3, state.inventory(session).receipts().size());
        assertEquals(3, state.grantSequence());
        assertEquals(4, state.offers().get(session).turnRevision());
        assertEquals(0, state.offers().get(session).pickerScalar());
        assertEquals(admission, state.offers().get(session).admissionId());
        var receipt = state.inventory(session).receipts().getFirst();
        assertEquals(MatchItemSource.END_TURN_BONUS, receipt.source());
        assertEquals(admission, receipt.admissionId());
        assertEquals(OptionalLong.empty(), receipt.boxEntityId());
        assertEquals(Optional.empty(), receipt.scenarioHash());
        assertEquals(session, state.offers().get(session).sessionId());
        var stranger = UUID.randomUUID();
        assertSame(state, state.expired(stranger));
        assertSame(state, state.without(stranger));
        var expired = state.expired(session);
        assertEquals(state.inventories(), expired.inventories());
        assertEquals(0, expired.offers().size());
        assertSame(expired, expired.expired(session));
        var confirmed = expired.confirmed(session, admission, 0, 5);
        assertEquals(true, confirmed.inventory(session).receipts().getFirst().confirmed());
        var discarded = confirmed.discarded(session, 0, 5);
        assertEquals(65535, discarded.inventory(session).slots().getFirst());
        assertEquals(0, discarded.without(session).inventories().size());
        assertEquals(0, state.without(session).offers().size());
        assertEquals(0, initial.inventories().size());
        assertThrows(UnsupportedOperationException.class, () -> confirmed.inventories().clear());
        assertThrows(UnsupportedOperationException.class, () -> confirmed.offers().clear());
    }

    private MatchItemReceipt receipt(long sequence, int index, int item) {
        return new MatchItemReceipt(
            sequence,
            sequence * 2,
            index,
            item,
            false,
            ADMISSION,
            MatchItemSource.END_TURN_BONUS,
            OptionalLong.empty(),
            Optional.empty()
        );
    }
}

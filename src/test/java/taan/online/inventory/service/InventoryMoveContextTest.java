package taan.online.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.model.InventoryMoveContext;
import taan.online.inventory.service.model.MoveInventoryItemCommand;

class InventoryMoveContextTest {
    @Test
    void rejectsEquipmentChangesWithoutApplyingOrPublishingThemAndAllowsUnchangedRequests() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var contexts = new TestInventoryMoveContextAccess();
        contexts.allowed = false;
        var original = repository.stored;
        var action = new MoveInventoryItemAction(
            id -> Optional.of(actor),
            repository,
            InventoryFixtures.catalog(),
            contexts
        );
        var result = action.execute(actor.sessionId(), InventoryFixtures.command(repository));

        assertEquals(InventoryMoveOutcome.STATE_RESTRICTED, result.outcome());
        assertEquals(0, repository.writes);
        assertEquals(original, repository.stored);
        assertEquals(2, result.inventories().size());
        assertTrue(contexts.equipment.isEmpty());
        var item = original.inventories().get(1).items().getFirst();
        var same = new MoveInventoryItemCommand(
            1,
            new InventoryMove(10011, item.id().value(), item.slot(), item.itemId(), 10011, item.slot(), item.itemId())
        );

        assertEquals(InventoryMoveOutcome.NO_CHANGE, action.execute(actor.sessionId(), same).outcome());
        assertTrue(contexts.equipment.isEmpty());
        assertThrows(NullPointerException.class, () -> new InventoryMoveContext(null, null, true));
    }

    @Test
    void allowsBagReorderingEvenWhenEquipmentIsLockedWithoutPublishingAppearance() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var contexts = new TestInventoryMoveContextAccess();
        contexts.allowed = false;
        contexts.current = false;
        var action = new MoveInventoryItemAction(
            id -> Optional.of(actor),
            repository,
            InventoryFixtures.catalog(),
            contexts
        );
        action.execute(actor.sessionId(), InventoryFixtures.command(repository));
        contexts.allowed = true;
        contexts.current = true;
        action.execute(actor.sessionId(), InventoryFixtures.command(repository));
        var bag = repository.stored.inventories().getFirst();
        var item = bag.items().getFirst();
        contexts.equipment.clear();
        contexts.allowed = false;
        contexts.current = false;
        var move = new MoveInventoryItemCommand(
            1,
            new InventoryMove(bag.id().value(), item.id().value(), item.slot(), item.itemId(), bag.id().value(), 78, 0)
        );

        assertEquals(InventoryMoveOutcome.MOVED, action.execute(actor.sessionId(), move).outcome());
        assertTrue(contexts.equipment.isEmpty());
    }

    @Test
    void rollsBackWhenTheRoomContextChangesBeforeApplyOrBeforeCommit() {
        for (int boundary = 1; boundary <= 2; boundary++) {
            var actor = InventoryFixtures.actor();
            var repository = new TestInventoryRepository();
            var contexts = new TestInventoryMoveContextAccess();
            var calls = new AtomicInteger();
            int rejectAt = boundary;
            contexts.onCurrent = () -> contexts.current = calls.incrementAndGet() < rejectAt;
            var before = repository.stored;
            var action = new MoveInventoryItemAction(
                id -> Optional.of(actor),
                repository,
                InventoryFixtures.catalog(),
                contexts
            );
            var result = action.execute(actor.sessionId(), InventoryFixtures.command(repository));

            assertEquals(InventoryMoveOutcome.STATE_RESTRICTED, result.outcome());
            assertEquals(boundary - 1, repository.writes);
            assertEquals(0, repository.commits);
            assertEquals(1, repository.rollbacks);
            assertEquals(before, repository.stored);
            assertEquals(before.inventories().get(1), result.inventories().getFirst());
            assertTrue(contexts.equipment.isEmpty());
        }
    }

    @Test
    void publishesTheCommittedSnapshotAfterCommitAndNeverPublishesARolledBackMove() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var contexts = new TestInventoryMoveContextAccess();
        var action = new MoveInventoryItemAction(
            id -> Optional.of(actor),
            repository,
            InventoryFixtures.catalog(),
            contexts
        );
        repository.onCommit = () -> assertTrue(contexts.equipment.isEmpty());
        contexts.onEquipmentChanged = () -> assertEquals(1, repository.commits);
        var result = action.execute(actor.sessionId(), InventoryFixtures.command(repository));

        assertEquals(InventoryMoveOutcome.MOVED, result.outcome());
        assertEquals(1, contexts.equipment.size());
        assertEquals(repository.stored.inventories().get(1), contexts.equipment.getFirst());
        assertFalse(contexts.equipment.getFirst().items().stream().anyMatch(item -> item.slot() == 1));
    }
}

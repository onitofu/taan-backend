package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.MatchRoomFixture;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.model.InventoryState;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.inventory.service.model.MoveInventoryItemCommand;
import taan.online.room.model.RoomMode;
import taan.online.session.service.SessionInventoryAccess;

class MatchInventoryGuardTest {
    @Test
    void blocksEquipmentButAllowsBagMovesWithoutChangingFrozenAppearance() {
        for (boolean commit : new boolean[]{false, true}) {
            try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                var repository = new TestInventoryRepository();
                var containers = repository.stored.inventories();
                var bag = containers.getFirst();
                var equipment = containers.get(1);
                var source = equipment.items().getFirst();
                var item = new InventoryItem(
                    new InventoryItemId(9000),
                    0,
                    source.itemId(),
                    1,
                    source.palette1Id(),
                    source.palette2Id(),
                    source.palette3Id()
                );
                repository.stored = new InventoryState(
                    List.of(new CharacterInventory(bag.id(), bag.kind(), List.of(item)), equipment, containers.get(2))
                );
                var action = new MoveInventoryItemAction(
                    new SessionInventoryAccess(f.scenario.sessions),
                    repository,
                    InventoryFixtures.catalog(),
                    new RoomInventoryMoveContextAccess(f.scenario.rooms)
                );
                var token = f.scenario.rooms.reserveMatch(f.owner).token();

                if (commit) {
                    f.scenario.rooms.commitMatch(token).toCompletableFuture().join();
                }

                var frozen = f.scenario.matches.current(token).orElseThrow();
                assertEquals(
                    InventoryMoveOutcome.STATE_RESTRICTED,
                    action.execute(f.owner, InventoryFixtures.command(repository)).outcome()
                );
                assertEquals(0, repository.writes);
                var result = action.execute(
                    f.owner,
                    new MoveInventoryItemCommand(
                        1,
                        new InventoryMove(bag.id().value(), item.id().value(), 0, item.itemId(), bag.id().value(), 1, 0)
                    )
                );

                assertEquals(InventoryMoveOutcome.MOVED, result.outcome());
                assertEquals(1, repository.commits);
                assertTrue(repository.stored.inventories().getFirst().items().stream().anyMatch(i -> i.slot() == 1));
                assertEquals(equipment, repository.stored.inventories().get(1));
                assertEquals(frozen, f.scenario.matches.current(token).orElseThrow());
            }
        }
    }
}

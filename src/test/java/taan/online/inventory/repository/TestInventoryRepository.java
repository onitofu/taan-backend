package taan.online.inventory.repository;

import java.util.ArrayList;
import java.util.List;
import taan.online.SelectionFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryState;
import taan.online.inventory.model.ItemPlacement;

public final class TestInventoryRepository implements InventoryRepository {
    public InventoryState stored = new InventoryState(SelectionFixtures.inventories(AccountGender.FEMALE));
    public Runnable onBegin = () -> { };
    public Runnable onState = () -> { };
    public Runnable onApply = () -> { };
    public Runnable onCommit = () -> { };
    public RuntimeException failure;
    public int reads;
    public int begins;
    public int writes;
    public int commits;
    public int rollbacks;

    @Override
    public InventoryTransaction beginTransaction(AccountId owner, long characterId) {
        begins++;
        onBegin.run();
        fail();
        return new TestInventoryTransaction(this);
    }

    void fail() {
        if (failure != null) {
            throw failure;
        }
    }

    InventoryState relocate(InventoryState state, List<ItemPlacement> placements) {
        var all = state.inventories().stream().flatMap(value -> value.items().stream()).toList();
        var moved = placements.stream().map(ItemPlacement::itemId).toList();
        return new InventoryState(
            state.inventories().stream().map(
                container -> {
                    var items = new ArrayList<>(
                        container.items().stream()
                            .filter(item -> !moved.contains(item.id())).toList()
                    );

                    for (var placement : placements) {
                        if (placement.inventoryId().equals(container.id())) {
                            var item = all.stream().filter(
                                value -> value.id().equals(
                                    placement.itemId()
                                )
                            ).findFirst().orElseThrow();
                            items.add(
                                new InventoryItem(
                                    item.id(),
                                    placement.slot(),
                                    item.itemId(),
                                    item.quantity(),
                                    item.palette1Id(),
                                    item.palette2Id(),
                                    item.palette3Id()
                                )
                            );
                        }
                    }

                    return new CharacterInventory(container.id(), container.kind(), items);
                }
            ).toList()
        );
    }
}

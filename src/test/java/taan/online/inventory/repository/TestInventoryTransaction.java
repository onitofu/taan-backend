package taan.online.inventory.repository;

import java.util.List;
import taan.online.inventory.model.InventoryState;
import taan.online.inventory.model.ItemPlacement;

final class TestInventoryTransaction implements InventoryTransaction {
    private final TestInventoryRepository repository;
    private InventoryState state;
    private boolean committed;

    TestInventoryTransaction(TestInventoryRepository repository) {
        this.repository = repository;
        state = repository.stored;
    }

    @Override
    public InventoryState state() {
        repository.reads++;
        repository.onState.run();
        repository.fail();
        return state;
    }

    @Override
    public void apply(List<ItemPlacement> placements) {
        repository.writes++;
        repository.onApply.run();
        repository.fail();
        state = repository.relocate(state, placements);
    }

    @Override
    public void commit() {
        repository.onCommit.run();
        repository.fail();
        repository.stored = state;
        repository.commits++;
        committed = true;
    }

    @Override
    public void close() {
        if (!committed) {
            repository.rollbacks++;
        }
    }
}

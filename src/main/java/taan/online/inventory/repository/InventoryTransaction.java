package taan.online.inventory.repository;

import java.util.List;
import taan.online.inventory.model.InventoryState;
import taan.online.inventory.model.ItemPlacement;

public interface InventoryTransaction extends AutoCloseable {
    InventoryState state();

    void apply(List<ItemPlacement> placements);

    void commit();

    @Override
    void close();
}

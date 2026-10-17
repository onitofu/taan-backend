package taan.online.inventory.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import taan.online.inventory.model.InventoryState;
import taan.online.inventory.model.ItemPlacement;
import taan.online.inventory.repository.InventoryTransaction;

final class JdbcInventoryTransaction implements InventoryTransaction {
    private final Connection connection;
    private final JdbcInventoryRepository inventories;
    private final long owner;
    private final long characterId;
    private final int timeout;
    private boolean committed;

    JdbcInventoryTransaction(
        Connection connection,
        JdbcInventoryRepository inventories,
        long owner,
        long characterId,
        int timeout
    ) {
        this.connection = connection;
        this.inventories = inventories;
        this.owner = owner;
        this.characterId = characterId;
        this.timeout = timeout;
    }

    @Override
    public InventoryState state() {
        try {
            return new InventoryState(inventories.read(connection, owner, characterId));
        } catch (SQLException | IllegalArgumentException failure) {
            throw new InventoryPersistenceException(failure);
        }
    }

    @Override
    public void apply(List<ItemPlacement> placements) {
        String sql = """
            UPDATE inventory_items e SET inventory_id = ?, slot = ?
            FROM inventories old, inventories target
            WHERE e.id = ? AND e.inventory_id = old.id AND target.id = ?
                AND old.character_id = ? AND target.character_id = ?
            """;

        try (var constraint = connection.createStatement(); var statement = connection.prepareStatement(sql)) {
            constraint.setQueryTimeout(timeout);
            constraint.execute("SET CONSTRAINTS inventory_items_inventory_id_slot_key DEFERRED");
            statement.setQueryTimeout(timeout);

            for (var placement : placements) {
                statement.setLong(1, placement.inventoryId().value());
                statement.setInt(2, placement.slot());
                statement.setLong(3, placement.itemId().value());
                statement.setLong(4, placement.inventoryId().value());
                statement.setLong(5, characterId);
                statement.setLong(6, characterId);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Inventory placement is no longer owned", "22000");
                }
            }
        } catch (SQLException failure) {
            throw new InventoryPersistenceException(failure);
        }
    }

    @Override
    public void commit() {
        try {
            connection.commit();
            committed = true;
        } catch (SQLException failure) {
            throw new InventoryPersistenceException(failure);
        }
    }

    @Override
    public void close() {
        try (connection) {
            if (!committed) {
                connection.rollback();
            }
        } catch (SQLException failure) {
            throw new InventoryPersistenceException(failure);
        }
    }
}

package taan.online.inventory.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryId;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryKind;
import taan.online.inventory.model.StarterEquipment;

public final class JdbcInventoryRepository {
    private final int timeout;

    public JdbcInventoryRepository(int timeout) {
        this.timeout = timeout;
    }

    public void create(Connection connection, long characterId, StarterEquipment starter) throws SQLException {
        for (var kind : InventoryKind.values()) {
            long inventoryId;

            try (var statement = connection.prepareStatement(
                "INSERT INTO inventories (character_id, kind) VALUES (?, ?) RETURNING id"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, characterId);
                statement.setString(2, kind.name());

                try (var rows = statement.executeQuery()) {
                    rows.next();
                    inventoryId = rows.getLong(1);
                }
            }

            if (kind == InventoryKind.EQUIPMENT) {
                insertItems(connection, inventoryId, starter);
            }
        }
    }

    public List<CharacterInventory> read(Connection connection, long owner, long characterId) throws SQLException {
        String sql = """
            SELECT i.id, i.kind
            FROM inventories i JOIN characters c ON c.id = i.character_id
            WHERE c.account_id = ? AND c.id = ? AND c.deleted_at IS NULL
            ORDER BY i.id
            """;
        var result = new ArrayList<CharacterInventory>();

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, owner);
            statement.setLong(2, characterId);

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var id = new InventoryId(rows.getLong("id"));
                    result.add(
                        new CharacterInventory(
                            id,
                            InventoryKind.valueOf(rows.getString("kind")),
                            items(connection, id.value())
                        )
                    );
                }
            }
        }

        if (result.stream().map(CharacterInventory::kind).distinct().count() != InventoryKind.values().length) {
            throw new SQLException("Incomplete character inventories", "22000");
        }

        return List.copyOf(result);
    }

    private void insertItems(Connection connection, long inventoryId, StarterEquipment starter) throws SQLException {
        try (var statement = connection.prepareStatement(
            "INSERT INTO inventory_items (inventory_id, slot, item_id, parameter0, parameter1, parameter2) "
                + "VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            statement.setQueryTimeout(timeout);

            for (var item : starter.items()) {
                statement.setLong(1, inventoryId);
                statement.setInt(2, item.slot());
                statement.setLong(3, item.itemId());
                statement.setInt(4, item.parameter0());
                statement.setInt(5, item.parameter1());
                statement.setInt(6, item.parameter2());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private List<InventoryItem> items(Connection connection, long inventoryId) throws SQLException {
        var result = new ArrayList<InventoryItem>();

        try (var statement = connection.prepareStatement(
            "SELECT id, slot, item_id, quantity, parameter0, parameter1, parameter2 "
                + "FROM inventory_items WHERE inventory_id = ? ORDER BY slot"
        )) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, inventoryId);

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(
                        new InventoryItem(
                            new InventoryItemId(rows.getLong("id")),
                            rows.getInt("slot"),
                            rows.getLong("item_id"),
                            rows.getInt("quantity"),
                            rows.getInt("parameter0"),
                            rows.getInt("parameter1"),
                            rows.getInt("parameter2")
                        )
                    );
                }
            }
        }

        return List.copyOf(result);
    }
}

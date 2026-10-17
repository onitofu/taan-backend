package taan.online.inventory.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import taan.online.account.model.AccountId;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryId;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryKind;
import taan.online.inventory.model.StarterEquipment;
import taan.online.inventory.repository.InventoryRepository;
import taan.online.inventory.repository.InventoryTransaction;

public final class JdbcInventoryRepository implements InventoryRepository {
    private final DataSource source;
    private final int timeout;

    public JdbcInventoryRepository(DataSource source, int timeout) {
        this.source = source;
        this.timeout = timeout;
    }

    @Override
    public InventoryTransaction beginTransaction(AccountId owner, long characterId) {
        Connection connection = null;

        try {
            connection = source.getConnection();
            connection.setAutoCommit(false);

            try (var lock = connection.prepareStatement("SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
                lock.setQueryTimeout(timeout);
                lock.setLong(1, owner.value());
                lock.executeQuery().close();
            }

            String sql = "SELECT id FROM characters WHERE id = ? AND account_id = ? AND deleted_at IS NULL FOR UPDATE";

            try (var lock = connection.prepareStatement(sql)) {
                lock.setQueryTimeout(timeout);
                lock.setLong(1, characterId);
                lock.setLong(2, owner.value());

                try (var rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Inventory owner is no longer active", "22000");
                    }
                }
            }

            try (var lock = connection.prepareStatement(
                "SELECT id FROM inventories WHERE character_id = ? ORDER BY id FOR UPDATE"
            )) {
                lock.setQueryTimeout(timeout);
                lock.setLong(1, characterId);
                lock.executeQuery().close();
            }

            try (var lock = connection.prepareStatement(
                "SELECT e.id FROM inventory_items e JOIN inventories i ON i.id = e.inventory_id "
                    + "WHERE i.character_id = ? ORDER BY e.id FOR UPDATE OF e"
            )) {
                lock.setQueryTimeout(timeout);
                lock.setLong(1, characterId);
                lock.executeQuery().close();
            }

            return new JdbcInventoryTransaction(connection, this, owner.value(), characterId, timeout);
        } catch (SQLException failure) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }

            throw new InventoryPersistenceException(failure);
        }
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
            "INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, palette_2_id, palette_3_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            statement.setQueryTimeout(timeout);

            for (var item : starter.items()) {
                statement.setLong(1, inventoryId);
                statement.setInt(2, item.slot());
                statement.setLong(3, item.itemId());
                statement.setInt(4, item.palette1Id());
                statement.setInt(5, item.palette2Id());
                statement.setInt(6, item.palette3Id());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private List<InventoryItem> items(Connection connection, long inventoryId) throws SQLException {
        var result = new ArrayList<InventoryItem>();

        try (var statement = connection.prepareStatement(
            "SELECT id, slot, item_id, quantity, palette_1_id, palette_2_id, palette_3_id "
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
                            rows.getInt("palette_1_id"),
                            rows.getInt("palette_2_id"),
                            rows.getInt("palette_3_id")
                        )
                    );
                }
            }
        }

        return List.copyOf(result);
    }
}

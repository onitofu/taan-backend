package taan.online.character.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import taan.online.account.model.AccountId;
import taan.online.character.model.GameCharacter;

final class CharacterRowReader {
    private CharacterRowReader() {
    }

    static List<GameCharacter> read(Connection connection, AccountId owner, int timeout) throws SQLException {
        String sql = """
            SELECT c.id, c.account_id, c.name, c.level, c.created_at, a.gender,
                   i.id AS equipment_id, p.slot, p.item_id, p.palette_1_id, p.palette_2_id, p.palette_3_id
            FROM characters c
            JOIN accounts a ON a.id = c.account_id
            LEFT JOIN inventories i ON i.character_id = c.id AND i.kind = 'EQUIPMENT'
            LEFT JOIN inventory_items p ON p.inventory_id = i.id
            WHERE c.account_id = ? AND c.deleted_at IS NULL
            ORDER BY c.id, p.slot
            """;
        var characters = new LinkedHashMap<Long, CharacterRow>();

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, owner.value());

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long id = rows.getLong("id");
                    var row = characters.get(id);

                    if (row == null) {
                        row = new CharacterRow(rows);
                        characters.put(id, row);
                    }

                    row.addPart(rows);
                }
            }
        }

        return characters.values().stream().map(CharacterRow::character).toList();
    }
}

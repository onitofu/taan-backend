package taan.online.inventory.repository.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import taan.online.inventory.model.ItemDefinition;
import taan.online.inventory.model.ItemGender;
import taan.online.inventory.repository.ItemDefinitionRepository;

public final class JdbcItemDefinitionRepository implements ItemDefinitionRepository {
    private final DataSource source;
    private final int timeout;

    public JdbcItemDefinitionRepository(DataSource source, int timeout) {
        this.source = source;
        this.timeout = timeout;
    }

    @Override
    public List<ItemDefinition> load() {
        String sql = """
            SELECT item_id, name, equipment_slot, gender, default_palette_1_id,
                   default_palette_2_id, default_palette_3_id, resource_available
            FROM item_definitions ORDER BY item_id
            """;
        var result = new ArrayList<ItemDefinition>();

        try (var connection = source.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    ItemGender gender = switch (rows.getInt("gender")) {
                        case 0 -> ItemGender.UNRESTRICTED;
                        case 1 -> ItemGender.MALE;
                        case 2 -> ItemGender.FEMALE;
                        default -> throw new SQLException("Invalid item definition gender", "22000");
                    };
                    result.add(
                        new ItemDefinition(
                            rows.getLong("item_id"),
                            rows.getString("name"),
                            rows.getObject("equipment_slot", Integer.class),
                            gender,
                            rows.getInt("default_palette_1_id"),
                            rows.getInt("default_palette_2_id"),
                            rows.getInt("default_palette_3_id"),
                            rows.getBoolean("resource_available")
                        )
                    );
                }
            }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new InventoryPersistenceException(failure);
        }

        return List.copyOf(result);
    }
}

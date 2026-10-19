package taan.online.channel.repository.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.channel.repository.ChannelRepository;

public final class JdbcChannelRepository implements ChannelRepository {
    private final DataSource source;
    private final int timeout;

    public JdbcChannelRepository(DataSource source, int timeout) {
        this.source = source;
        this.timeout = timeout;
    }

    @Override
    public List<ChannelGroupDefinition> load() {
        String sql = """
            SELECT g.id AS group_id, g.name AS group_name, g.type, c.id, c.name,
                   c.enabled, c.min_level, c.max_level, c.capacity, c.kind
            FROM channel_groups g LEFT JOIN channels c ON c.group_id = g.id
            ORDER BY g.sort_order, g.id, c.sort_order, c.id
            """;
        var groups = new ArrayList<ChannelGroupDefinition>();

        try (var connection = source.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            int groupId = 0;
            String groupName = "";
            int type = 0;
            var channels = new ArrayList<ChannelDefinition>();

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    int nextId = rows.getInt("group_id");

                    if (nextId != groupId) {
                        if (groupId != 0) {
                            groups.add(new ChannelGroupDefinition(groupId, groupName, type, channels));
                        }

                        groupId = nextId;
                        groupName = rows.getString("group_name");
                        type = rows.getInt("type");
                        channels = new ArrayList<>();
                    }

                    if (rows.getObject("id") != null) {
                        channels.add(
                            new ChannelDefinition(
                                rows.getInt("id"),
                                rows.getString("name"),
                                rows.getBoolean("enabled"),
                                rows.getInt("min_level"),
                                rows.getInt("max_level"),
                                rows.getInt("capacity"),
                                rows.getInt("kind")
                            )
                        );
                    }
                }
            }

            if (groupId != 0) {
                groups.add(new ChannelGroupDefinition(groupId, groupName, type, channels));
            }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new ChannelPersistenceException(failure);
        }

        return List.copyOf(groups);
    }
}

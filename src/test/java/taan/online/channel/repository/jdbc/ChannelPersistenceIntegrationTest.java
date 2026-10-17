package taan.online.channel.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.CharacterTestDatabase;
import taan.online.persistence.DatabaseMigrator;

class ChannelPersistenceIntegrationTest {
    private static CharacterTestDatabase database;

    @BeforeAll
    static void start() {
        database = new CharacterTestDatabase();
    }

    @AfterAll
    static void stop() {
        if (database != null) {
            database.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        database.execute("DELETE FROM channels");
        database.execute("DELETE FROM channel_groups");
    }

    @Test
    void upgradesV5WithoutChangingExistingCharactersAndSeedsStableChannelIdsOnlyOnce() throws Exception {
        database.execute("CREATE SCHEMA channels_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("channels_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("channels_upgrade").target("5").load().migrate();

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(
                "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('original', 'public-verifier', 1)"
            );
            statement.execute("INSERT INTO characters (account_id, name) VALUES (1, 'OriginalHero')");
        }

        new DatabaseMigrator().migrate(source);
        new DatabaseMigrator().migrate(source);
        var groups = new JdbcChannelRepository(source, 5).load();

        assertEquals(1, groups.size());
        assertEquals("Solar", groups.getFirst().name());
        assertEquals(0, groups.getFirst().type());
        assertEquals(List.of(1, 2), groups.getFirst().channels().stream().map(channel -> channel.id()).toList());
        assertEquals(
            List.of("Moon", "Terra"),
            groups.getFirst().channels().stream().map(channel -> channel.name()).toList()
        );
        assertTrue(
            groups.getFirst().channels().stream().allMatch(
                channel -> channel.enabled() && channel.capacity() == 100
                    && channel.minLevel() == 1 && channel.maxLevel() == 109
            )
        );

        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT id, name FROM characters")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals("OriginalHero", rows.getString(2));
            assertFalse(rows.next());
        }
    }

    @Test
    void loadsOrderedGroupsAndEmptyGroupsAndReturnsIndependentSnapshots() throws Exception {
        database.execute(
            "INSERT INTO channel_groups (name, type, sort_order) "
                + "VALUES ('Second', 6, 2), ('First', 1, 1), ('Empty', 0, 0)"
        );
        database.execute(
            "INSERT INTO channels (id, group_id, name, capacity, min_level, max_level, sort_order) "
                + "SELECT c.id, g.id, c.name, 2, 10, 50, c.sort_order FROM channel_groups g "
                + "CROSS JOIN (VALUES (20, 'Later', 1), (10, 'Earlier', 0)) c(id, name, sort_order) "
                + "WHERE g.name = 'First'"
        );
        var repository = new JdbcChannelRepository(database.source(), 5);
        var original = repository.load();

        assertEquals(List.of("Empty", "First", "Second"), original.stream().map(group -> group.name()).toList());
        assertTrue(original.getFirst().channels().isEmpty());
        assertEquals(List.of(10, 20), original.get(1).channels().stream().map(channel -> channel.id()).toList());
        assertEquals(2, original.get(1).channels().getFirst().capacity());
        assertEquals(10, original.get(1).channels().getFirst().minLevel());
        assertEquals(50, original.get(1).channels().getFirst().maxLevel());
        assertThrows(UnsupportedOperationException.class, () -> original.clear());

        database.execute("UPDATE channels SET name = 'Changed', enabled = false WHERE id = 10");
        var changed = repository.load();

        assertEquals("Earlier", original.get(1).channels().getFirst().name());
        assertEquals("Changed", changed.get(1).channels().getFirst().name());
        assertFalse(changed.get(1).channels().getFirst().enabled());

        database.execute("DELETE FROM channels");
        database.execute("DELETE FROM channel_groups");

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void enforcesKeysNamesBoundsOwnershipAndRejectsInvalidConfigurationWithoutLeakingValues() throws Exception {
        database.execute("INSERT INTO channel_groups (name) VALUES ('Solar')");
        database.execute("INSERT INTO channels (id, group_id, name) SELECT 1, id, 'Moon' FROM channel_groups");

        for (String sql : List.of(
            "UPDATE channels SET id = 0",
            "UPDATE channels SET id = 65535",
            "UPDATE channels SET group_id = -1",
            "UPDATE channels SET capacity = 0",
            "UPDATE channels SET min_level = 0",
            "UPDATE channels SET max_level = 110",
            "UPDATE channels SET min_level = 20, max_level = 10",
            "UPDATE channels SET name = ''",
            "UPDATE channels SET name = repeat('x', 65)",
            "UPDATE channels SET sort_order = -1",
            "INSERT INTO channel_groups (id, name) OVERRIDING SYSTEM VALUE VALUES (0, 'Zero')",
            "UPDATE channel_groups SET name = ''",
            "UPDATE channel_groups SET type = -1",
            "UPDATE channel_groups SET type = 7",
            "UPDATE channel_groups SET sort_order = -1",
            "DELETE FROM channel_groups",
            "INSERT INTO channels (id, group_id, name) SELECT 1, id, 'Duplicate' FROM channel_groups"
        )) {
            assertThrows(SQLException.class, () -> database.execute(sql));
        }

        database.execute("UPDATE channels SET name = chr(9)");
        var repository = new JdbcChannelRepository(database.source(), 5);
        var invalid = assertThrows(ChannelPersistenceException.class, repository::load);

        assertFalse(invalid.toString().contains("Moon"));

        database.execute("ALTER TABLE channels RENAME TO unavailable_channels");
        var missing = assertThrows(ChannelPersistenceException.class, repository::load);

        assertFalse(missing.getCause().toString().contains("SELECT"));
        database.execute("ALTER TABLE unavailable_channels RENAME TO channels");
    }
}

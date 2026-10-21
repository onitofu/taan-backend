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
    void upgradesExistingChannelsWithKindZeroAndReloadsIndependentCommittedSettings() throws Exception {
        database.execute("CREATE SCHEMA channel_kind_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("channel_kind_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("channel_kind_upgrade").target("7").load().migrate();

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("UPDATE channel_groups SET type = 4");
        }

        new DatabaseMigrator().migrate(source);
        var before = new JdbcChannelRepository(source, 5).load();
        assertEquals(4, before.getFirst().type());
        assertTrue(before.getFirst().channels().stream().allMatch(channel -> channel.kind() == 0));

        for (int kind : new int[]{0, 1, 4, 6}) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("UPDATE channels SET kind = " + kind + " WHERE id = 1");
            }

            var reloaded = new JdbcChannelRepository(source, 5).load();
            assertEquals(kind, reloaded.getFirst().channels().getFirst().kind());
            assertEquals(0, reloaded.getFirst().channels().get(1).kind());
            assertEquals(4, reloaded.getFirst().type());
        }

        new DatabaseMigrator().migrate(source);
        assertEquals(6, new JdbcChannelRepository(source, 5).load().getFirst().channels().getFirst().kind());
        assertEquals(0, before.getFirst().channels().getFirst().kind());
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
    void upgradesV8WithScalarZeroAndLoadsIndependentCommittedChannelLootSettings() throws Exception {
        database.execute("CREATE SCHEMA channel_loot_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("channel_loot_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("channel_loot_upgrade").target("8").load().migrate();
        new DatabaseMigrator().migrate(source);
        var original = new JdbcChannelRepository(source, 5).load();
        assertTrue(original.getFirst().channels().stream().allMatch(channel -> channel.lootPickerScalar() == 0));

        for (int scalar : new int[]{0, 49, 50, 69, 70, 89}) {
            try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "UPDATE channels SET loot_picker_scalar = ? WHERE id = 2"
            )) {
                statement.setInt(1, scalar);
                statement.executeUpdate();
            }

            var reloaded = new JdbcChannelRepository(source, 5).load().getFirst().channels();
            assertEquals(0, reloaded.getFirst().lootPickerScalar());
            assertEquals(scalar, reloaded.get(1).lootPickerScalar());
            assertEquals(0, original.getFirst().channels().get(1).lootPickerScalar());
        }

        new DatabaseMigrator().migrate(source);
        assertEquals(89, new JdbcChannelRepository(source, 5).load().getFirst().channels().get(1).lootPickerScalar());
    }

    @Test
    void expandsV9WithoutChangingExistingChannelsAndReloadsNuclearSettings() throws Exception {
        database.execute("CREATE SCHEMA channel_nuclear_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("channel_nuclear_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("channel_nuclear_upgrade").target("9").load().migrate();

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("UPDATE channels SET loot_picker_scalar = CASE WHEN id = 1 THEN 70 ELSE 89 END");
            assertThrows(SQLException.class, () -> statement.execute("UPDATE channels SET loot_picker_scalar = 90"));
        }

        var before = new JdbcChannelRepository(source, 5).load();
        new DatabaseMigrator().migrate(source);
        assertEquals(before, new JdbcChannelRepository(source, 5).load());

        for (int scalar : new int[]{90, 99, 100}) {
            try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "UPDATE channels SET loot_picker_scalar = ? WHERE id = 2"
            )) {
                statement.setInt(1, scalar);
                assertEquals(1, statement.executeUpdate());
            }

            var reloaded = new JdbcChannelRepository(source, 5).load().getFirst().channels();
            assertEquals(70, reloaded.getFirst().lootPickerScalar());
            assertEquals(scalar, reloaded.get(1).lootPickerScalar());
        }

        new DatabaseMigrator().migrate(source);
        assertEquals(100, new JdbcChannelRepository(source, 5).load().getFirst().channels().get(1).lootPickerScalar());
        assertEquals(89, before.getFirst().channels().get(1).lootPickerScalar());
    }

    @Test
    void enforcesKeysNamesBoundsOwnershipAndRejectsInvalidConfigurationWithoutLeakingValues() throws Exception {
        database.execute("INSERT INTO channel_groups (name) VALUES ('Solar')");
        database.execute("INSERT INTO channels (id, group_id, name) SELECT 1, id, 'Moon' FROM channel_groups");

        for (String sql : List.of(
            "UPDATE channels SET id = 0",
            "UPDATE channels SET kind = NULL",
            "UPDATE channels SET kind = -1",
            "UPDATE channels SET kind = 2",
            "UPDATE channels SET kind = 3",
            "UPDATE channels SET kind = 5",
            "UPDATE channels SET kind = 7",
            "UPDATE channels SET loot_picker_scalar = NULL",
            "UPDATE channels SET loot_picker_scalar = -1",
            "UPDATE channels SET loot_picker_scalar = 101",
            "UPDATE channels SET loot_picker_scalar = 32768",
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

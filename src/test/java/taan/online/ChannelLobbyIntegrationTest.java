package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.bootstrap.InvalidConfigurationException;
import taan.online.lobby.protocol.packet.UserSimpleInfo;

class ChannelLobbyIntegrationTest {
    private static CharacterTestDatabase database;

    private Map<Long, Integer> expectedGenderIcons;

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
        database.reset();
        database.execute("DELETE FROM channels WHERE id NOT IN (1, 2)");
        database.execute(
            "UPDATE channels SET name = CASE id WHEN 1 THEN 'Moon' ELSE 'Terra' END, enabled = true, "
                + "min_level = 1, max_level = 109, capacity = 100, kind = 0"
        );
    }

    @Test
    void maintainsLiveRostersAcrossTwoClientsSwitchesLeaveAndReconnectOnTheSameTcp() throws Exception {
        var a = database.account("a@example.test", AccountGender.MALE);
        var b = database.account("b@example.test", AccountGender.FEMALE);
        long first = database.create(a.id(), "HeroA", AccountGender.MALE).catalog().getFirst().id().value();
        long second = database.create(b.id(), "HeroB", AccountGender.FEMALE).catalog().getFirst().id().value();
        expectedGenderIcons = Map.of(first, 1, second, 2);
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var clientA = new CharacterTestClient(config.port()); var clientB = new CharacterTestClient(
                config.port()
            )) {
                select(clientA, a.login().value(), first);
                select(clientB, b.login().value(), second);
                clientA.send(SelectionFixtures.channels());

                SelectionFixtures.assertChannels(clientA.read(), config.port());
                assertEquals(List.of(first), enter(clientA, 1, 1));
                assertEquals(List.of(second), enter(clientB, 2, 1));

                clientA.send(SelectionFixtures.cash(0, 0));
                clientB.send(SelectionFixtures.cash(0, 0));

                assertArrayEquals(SelectionFixtures.cash(0, 0), clientA.read());
                assertArrayEquals(SelectionFixtures.money(0), clientA.read());
                assertArrayEquals(SelectionFixtures.cash(0, 0), clientB.read());
                assertArrayEquals(SelectionFixtures.money(0), clientB.read());

                assertEquals(List.of(first, second), enter(clientB, 1, 1));
                assertEquals(List.of(second), users(clientA.read()));

                clientB.send(ChannelFixtures.enter(0));

                assertEquals(List.of(first, second), bootstrap(clientB));

                clientA.sendTogether(ChannelFixtures.leave(), SelectionFixtures.channels());

                assertArrayEquals(ChannelFixtures.fixture("lobby-leave-response"), clientA.read());
                SelectionFixtures.assertChannels(clientA.read(), config.port());
                assertEquals(List.of(first), ChannelFixtures.removed(clientB.read()));
                assertEquals(List.of(first), enter(clientA, 2, 0));
                assertEquals(List.of(first, second), enter(clientB, 2, 0));
                assertEquals(List.of(second), users(clientA.read()));

                clientB.disconnect();

                assertEquals(List.of(second), ChannelFixtures.removed(clientA.read()));

                try (var reconnected = new CharacterTestClient(config.port())) {
                    select(reconnected, b.login().value(), second);

                    assertEquals(List.of(first, second), enter(reconnected, 2, 1));
                    assertEquals(List.of(second), users(clientA.read()));

                    reconnected.disconnect();

                    assertEquals(List.of(second), ChannelFixtures.removed(clientA.read()));
                }

                clientA.send(SelectionFixtures.cash(12345, 0));

                assertArrayEquals(SelectionFixtures.cash(0, 0), clientA.read());
                assertArrayEquals(SelectionFixtures.money(0), clientA.read());

                clientA.send(SelectionFixtures.logging(first));
                clientA.disconnect();
            }
        }
    }

    @Test
    void rejectsForgedClosedFullAndOutOfRangeEntriesAndRetainsThePreviousSeat() throws Exception {
        database.execute("UPDATE channels SET capacity = 1");
        database.execute("UPDATE channels SET min_level = 50 WHERE id = 2");
        database.execute(
            "INSERT INTO channels (id, group_id, name, enabled) "
                + "SELECT 3, group_id, 'Closed', false FROM channels WHERE id = 1"
        );
        database.execute(
            "INSERT INTO channels (id, group_id, name, max_level) "
                + "SELECT 4, group_id, 'Novice', 1 FROM channels WHERE id = 1"
        );
        var a = database.account("a@example.test", AccountGender.MALE);
        var b = database.account("b@example.test", AccountGender.MALE);
        long first = database.create(a.id(), "HeroA", AccountGender.MALE).catalog().getFirst().id().value();
        long second = database.create(b.id(), "HeroB", AccountGender.MALE).catalog().getFirst().id().value();
        expectedGenderIcons = Map.of(first, 1, second, 1);
        database.execute("UPDATE characters SET level = 2 WHERE id = " + second);
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var clientA = new CharacterTestClient(config.port()); var clientB = new CharacterTestClient(
                config.port()
            )) {
                select(clientA, a.login().value(), first);
                select(clientB, b.login().value(), second);

                assertEquals(List.of(first), enter(clientA, 1, 1));

                clientB.send(ChannelFixtures.enter(1));

                assertEquals(4, clientB.read()[8]);

                for (int[] rejection : new int[][]{{1, 100}, {2, 102}, {3, 103}, {4, 101}, {99, 255}}) {
                    clientB.sendFragmented(ChannelFixtures.move(rejection[0]));
                    byte[] response = clientB.read();

                    assertEquals(0x33cf0634, ChannelFixtures.type(response));
                    assertEquals(rejection[1], Byte.toUnsignedInt(response[4]));
                    assertTrue(response.length >= 18);
                }

                for (byte[] request : List.of(
                    ChannelFixtures.move(1, 1, 0, 929),
                    ChannelFixtures.move(1, 0, 1, 929),
                    ChannelFixtures.move(1, 0, 0, 928)
                )) {
                    clientA.send(request);

                    assertEquals(255, Byte.toUnsignedInt(clientA.read()[4]));
                }

                clientA.send(ChannelFixtures.move(2));

                assertEquals(102, Byte.toUnsignedInt(clientA.read()[4]));

                clientA.send(ChannelFixtures.leave());

                assertEquals(0x22910506, ChannelFixtures.type(clientA.read()));

                clientB.send(ChannelFixtures.move(1));

                assertEquals(100, Byte.toUnsignedInt(clientB.read()[4]));

                clientA.send(ChannelFixtures.enter(0));

                assertEquals(List.of(first), bootstrap(clientA));

                clientA.disconnect();

                assertEquals(List.of(second), enter(clientB, 1, 1));

                clientB.disconnect();
            }
        }
    }

    @Test
    void appliesDatabaseEditsOnlyAfterRestartAndChecksSnapshotBudgetsBeforeListening() throws Exception {
        var owner = database.account("a@example.test", AccountGender.MALE);
        long id = database.create(owner.id(), "HeroA", AccountGender.MALE).catalog().getFirst().id().value();
        expectedGenderIcons = Map.of(id, 1);
        var config = database.configuration();
        byte[] catalog;

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                select(client, owner.login().value(), id);
                client.send(SelectionFixtures.channels());
                catalog = client.read();
                database.execute("UPDATE channels SET name = 'Renamed', capacity = 1, kind = 6 WHERE id = 1");
                client.send(SelectionFixtures.channels());

                assertArrayEquals(catalog, client.read());
                client.send(ChannelFixtures.move(1));
                assertEquals(0, client.read()[36]);

                client.disconnect();
            }
        }

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                select(client, owner.login().value(), id);
                client.send(SelectionFixtures.channels());
                String wire = HexFormat.of().formatHex(client.read());

                assertTrue(wire.contains(HexFormat.of().formatHex("Renamed".getBytes(StandardCharsets.UTF_16LE))));
                assertFalse(wire.contains(HexFormat.of().formatHex("Moon".getBytes(StandardCharsets.UTF_16LE))));
                client.send(ChannelFixtures.move(1));
                assertEquals(6, client.read()[42]);

                client.disconnect();
            }
        }

        for (int limit : new int[]{100, 2000}) {
            var environment = TestFixtures.environment();
            environment.put("DB_HOSTNAME", config.databaseHost());
            environment.put("DB_PORT", Integer.toString(config.databasePort()));
            environment.put("POSTGRES_DB", config.databaseName());
            environment.put("POSTGRES_USER", config.databaseUser());
            environment.put("POSTGRES_PASSWORD", config.databasePassword());
            environment.put("BACKEND_MAX_FRAME_BYTES", Integer.toString(limit));

            try (var application = new Application(ApplicationConfig.fromEnvironment(environment))) {
                assertThrows(InvalidConfigurationException.class, application::start);
            }
        }
    }

    private void select(CharacterTestClient client, String login, long id) throws Exception {
        client.login(login);
        client.send(CharacterFixtures.select(id));

        assertEquals(0x3acb06af, ChannelFixtures.type(client.read()));
        SelectionFixtures.assertPlayableTime(client.read());
    }

    private List<Long> enter(CharacterTestClient client, int channel, int first) throws Exception {
        client.sendTogether(ChannelFixtures.move(channel), ChannelFixtures.enter(first));
        var response = ByteBuffer.wrap(client.read()).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x33cf0634, response.getInt());
        assertEquals(0, response.get());
        assertEquals(channel, Short.toUnsignedInt(response.getShort()));
        return bootstrap(client);
    }

    private List<Long> bootstrap(CharacterTestClient client) throws Exception {
        byte[] enter = client.read();

        assertEquals(0x22b60517, ChannelFixtures.type(enter));
        assertEquals(0, enter[8]);
        assertArrayEquals(ChannelFixtures.fixture("PcLobby_InitRoomList-empty"), client.read());
        byte[] users = client.read();

        assertEquals(0x501d07d9, ChannelFixtures.type(users));
        return users(users);
    }

    private List<Long> users(byte[] root) {
        var roster = ChannelFixtures.roster(root);
        roster.forEach(user -> assertEquals(expectedGenderIcons.get(user.characterId()), user.genderIcon()));
        return roster.stream().map(UserSimpleInfo::characterId).toList();
    }
}

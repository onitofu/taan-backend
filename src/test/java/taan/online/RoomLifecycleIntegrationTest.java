package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.leave;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.character.model.CharacterId;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionInit;

class RoomLifecycleIntegrationTest {
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
        database.reset();
    }

    @Test
    void createsLeavesAndReconnectsWithFreshDurableEquipmentAndDropsRoomsOnRestart() throws Exception {
        var account = database.account("room@example.test", AccountGender.MALE);
        long character = database.create(
            account.id(),
            "RoomHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();
        var config = database.configuration();
        CharacterAppear expected;

        try (var application = new Application(config)) {
            application.start();
            try (var client = new CharacterTestClient(config.port())) {
                lobby(client, account.login().value(), character, 1);
                database.execute(
                    "UPDATE inventory_items SET item_id = 1039, palette_1_id = 303 WHERE inventory_id IN "
                        + "(SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'EQUIPMENT') "
                        + "AND slot = 1"
                );
                client.sendFragmented(root("create-unprotected"));
                var first = initialize(client.read(), account.id().value(), character);
                expected = first.slots().getFirst().occupant().appearance();

                assertEquals(1039, expected.parts().get(1).itemId());
                assertEquals(303, expected.parts().get(1).palette1Id());
                client.send(ChannelFixtures.leave());

                assertEquals(0x22910506, ChannelFixtures.type(client.read()));
                client.send(root("start-empty"));

                assertEquals(
                    "The room is not ready to start.",
                    new NoticeMessageCodec().decode(client.read()).message()
                );
                client.sendTogether(leave(first.info().roomId(), 0), leave(first.info().roomId(), 0));

                assertEquals(1, new RoomResponseCodec().leave(client.read()).success());
                assertEquals(1, new RoomResponseCodec().leave(client.read()).success());
                client.send(ChannelFixtures.enter(0));
                bootstrap(client);
                client.send(root("create-unprotected"));
                var second = initialize(client.read(), account.id().value(), character);

                assertEquals(first.info().roomId(), second.info().roomId());
                assertEquals(expected, second.slots().getFirst().occupant().appearance());
                client.disconnect();
            }

            try (var client = new CharacterTestClient(config.port())) {
                lobby(client, account.login().value(), character, 1);
                client.send(root("create-unprotected"));
                var reconnected = initialize(client.read(), account.id().value(), character);

                assertEquals(expected, reconnected.slots().getFirst().occupant().appearance());
                assertEquals(1, reconnected.info().roomId());
                application.close();
                client.awaitClosure();
            }
        }

        try (var application = new Application(config)) {
            application.start();
            try (var client = new CharacterTestClient(config.port())) {
                lobby(client, account.login().value(), character, 1);
                client.send(root("create-unprotected"));
                var restarted = initialize(client.read(), account.id().value(), character);

                assertEquals(1, restarted.info().roomId());
                assertEquals(expected, restarted.slots().getFirst().occupant().appearance());
                client.disconnect();
            }
        }

        assertEquals(1, database.characters().findByAccount(account.id()).size());
        assertEquals(new CharacterId(character), database.characters().findByAccount(account.id()).getFirst().id());
    }

    @Test
    void keepsIndependentRoomsAndForgedLeavesIsolatedAcrossChannels() throws Exception {
        var a = database.account("room-a@example.test", AccountGender.MALE);
        var b = database.account("room-b@example.test", AccountGender.FEMALE);
        long first = database.create(a.id(), "RoomA", AccountGender.MALE).catalog().getFirst().id().value();
        long second = database.create(b.id(), "RoomB", AccountGender.FEMALE).catalog().getFirst().id().value();
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();
            try (var clientA = new CharacterTestClient(config.port()); var clientB = new CharacterTestClient(
                config.port()
            )) {
                lobby(clientA, a.login().value(), first, 1);
                lobby(clientB, b.login().value(), second, 2);
                clientA.send(root("create-unprotected"));
                clientB.send(root("create-unprotected"));
                var roomA = initialize(clientA.read(), a.id().value(), first);
                var roomB = initialize(clientB.read(), b.id().value(), second);

                assertNotEquals(roomA.info().roomId(), roomB.info().roomId());
                clientA.send(leave(roomB.info().roomId(), 0));

                assertEquals(0, new RoomResponseCodec().leave(clientA.read()).success());
                clientB.send(root("start-empty"));

                assertEquals(0x18dd043c, ChannelFixtures.type(clientB.read()));
                clientA.disconnect();
                clientB.send(leave(roomB.info().roomId(), 0));

                assertEquals(1, new RoomResponseCodec().leave(clientB.read()).success());
                clientB.disconnect();
            }
        }
    }

    private PcGameSessionInit initialize(byte[] bytes, long account, long character) {
        var room = new RoomResponseCodec().initialize(bytes);

        assertEquals(account, room.option().ownerAccountId());
        assertEquals(character, room.option().ownerCharacterId());
        assertEquals(account, room.slots().getFirst().occupant().accountId());
        assertEquals(character, room.slots().getFirst().occupant().characterId());
        assertEquals(2, room.slots().getFirst().state());
        assertEquals(1, room.info().details().playerCount());
        assertEquals(8, room.info().details().capacity());
        assertEquals(1, room.info().details().mapId());
        assertEquals(3, room.slots().getFirst().occupant().weaponId());
        return room;
    }

    private void lobby(CharacterTestClient client, String login, long character, int channel) throws Exception {
        client.login(login);
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));

        assertEquals(0x33cf0634, ChannelFixtures.type(client.read()));
        client.send(ChannelFixtures.enter(1));
        bootstrap(client);
    }

    private void bootstrap(CharacterTestClient client) throws Exception {
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        assertEquals(0x500c07d7, ChannelFixtures.type(client.read()));
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
    }
}

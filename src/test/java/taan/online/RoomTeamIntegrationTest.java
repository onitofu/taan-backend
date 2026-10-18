package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.team;

import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomTeamIntegrationTest {
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
        database.execute("ALTER SEQUENCE characters_id_seq RESTART WITH 1001");
    }

    @Test
    void replaysTheCapturedTeamRoundTripAndInitializesNewPlayersWithTheCurrentLayout() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("observer");
        var d = player("foreign");
        var config = database.configuration();
        var wire = new RoomResponseCodec();

        try (var app = new Application(config); var logs = new LogCapture()) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port());
                var observer = new CharacterTestClient(config.port());
                var foreign = new CharacterTestClient(config.port())
            ) {
                lobby(owner, a, 1);
                lobby(guest, b, 1);
                owner.read();
                lobby(observer, c, 1);
                owner.read();
                guest.read();
                lobby(foreign, d, 2);
                foreign.send(root("create-unprotected"));
                wire.initialize(foreign.read());
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.send(RoomEntryFixtures.enter(room));
                var initial = wire.initialize(guest.read());
                var original = initial.slots().get(2).occupant();
                wire.slot(owner.read());
                observer.read();
                observer.read();
                guest.sendFragmented(root("05-ChangeTeam.request"));

                assertArrayEquals(root("05-ChangeTeam.reply"), owner.read());
                assertArrayEquals(root("05-ChangeTeam.reply"), guest.read());
                guest.sendTogether(team(1), team(255));
                cash(guest);
                cash(owner);
                cash(observer);
                cash(foreign);
                guest.sendTogether(root("06-ChangeTeam.request"), team(2));

                assertArrayEquals(root("06-ChangeTeam.reply"), owner.read());
                assertArrayEquals(root("06-ChangeTeam.reply"), guest.read());
                cash(guest);
                guest.send(team(1));

                assertArrayEquals(root("05-ChangeTeam.reply"), owner.read());
                assertArrayEquals(root("05-ChangeTeam.reply"), guest.read());
                owner.send(team(2));
                var ownerTransfer = wire.changeTeam(owner.read());

                assertEquals(a.id().value(), ownerTransfer.accountId());
                assertEquals(0, ownerTransfer.oldSlot0());
                assertEquals(2, ownerTransfer.newSlot0());
                assertEquals(ownerTransfer, wire.changeTeam(guest.read()));
                cash(observer);
                observer.send(RoomEntryFixtures.enter(room));
                var joined = wire.initialize(observer.read());
                wire.slot(owner.read());
                wire.slot(guest.read());

                assertEquals(initial.option(), joined.option());
                var moved = joined.slots().get(1).occupant();
                assertEquals(original.accountId(), moved.accountId());
                assertEquals(original.characterId(), moved.characterId());
                assertEquals(original.name(), moved.name());
                assertEquals(original.appearance(), moved.appearance());
                assertEquals(original.weaponId(), moved.weaponId());
                assertEquals(1, moved.team());
                assertEquals(2, joined.slots().get(2).occupant().team());
                assertEquals(3, joined.info().details().playerCount());
                guest.disconnect();

                assertEquals(1, wire.slot(owner.read()).slot().slotIndex());
                assertEquals(1, wire.slot(observer.read()).slot().slotIndex());
                owner.disconnect();

                assertEquals(2, wire.slot(observer.read()).slot().slotIndex());
                assertEquals(c.id().value(), wire.option(observer.read()).option().ownerAccountId());
                observer.send(team(2));
                var transfer = wire.changeTeam(observer.read());

                assertEquals(c.id().value(), transfer.accountId());
                assertEquals(0, transfer.oldSlot0());
                assertEquals(2, transfer.newSlot0());
                cash(observer);
                cash(foreign);
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("bravo@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }
    }

    @Test
    void rejectsAFullTeamWithoutClosingTheConnectionAndDiscardsRoomStateOnRestart() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var config = database.configuration();
        var wire = new RoomResponseCodec();

        try (var app = new Application(config)) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                lobby(owner, a, 1);
                lobby(guest, b, 1);
                owner.read();
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();

                for (int slot : new int[]{2, 3, 6, 7}) {
                    owner.send(integer(root("PqGameSession_OpenCloseSlot-close"), 5, slot, 1));
                    wire.slotAvailability(owner.read());
                }

                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                guest.sendTogether(team(2), SelectionFixtures.cash(0, 0));

                assertEquals(
                    "No open slots on the requested team.",
                    new NoticeMessageCodec().decode(guest.read()).message()
                );
                assertArrayEquals(SelectionFixtures.cash(0, 0), guest.read());
                cash(owner);
                owner.send(integer(root("PqGameSession_OpenCloseSlot-open"), 5, 7, 1));
                wire.slotAvailability(owner.read());
                wire.slotAvailability(guest.read());
                guest.send(team(2));
                var changed = wire.changeTeam(guest.read());

                assertEquals(1, changed.oldSlot0());
                assertEquals(7, changed.newSlot0());
                assertEquals(changed, wire.changeTeam(owner.read()));
                guest.send(Arrays.copyOf(team(1), 4));
                var left = wire.slot(owner.read()).slot();

                assertEquals(7, left.slotIndex());
                assertEquals(0, left.state());
                cash(owner);
            }
        }

        try (var restarted = new Application(config)) {
            restarted.start();
            try (var owner = new CharacterTestClient(config.port())) {
                lobby(owner, a, 1);
                owner.send(root("create-unprotected"));
                var fresh = wire.initialize(owner.read());

                assertEquals(1, fresh.info().details().playerCount());
                assertEquals(1, fresh.slots().get(0).occupant().team());
                assertTrue(fresh.slots().stream().skip(1).allMatch(slot -> slot.state() == 0));
            }
        }
    }

    private Account player(String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        assertFalse(database.create(account.id(), "Hero" + name, AccountGender.FEMALE).catalog().isEmpty());
        return account;
    }

    private void lobby(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        long character = database.characters().findByAccount(account.id()).getFirst().id().value();
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        new LobbyRoomListCodec().initialize(client.read());
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
    }
}

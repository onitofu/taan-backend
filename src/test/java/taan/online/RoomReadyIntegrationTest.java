package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.ready;
import static taan.online.RoomScenario.team;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomReadyIntegrationTest {
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
    void replaysReadyCancelAndRepeatsOverTcpAndInitializesReadyOccupants() throws Exception {
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
                var created = wire.initialize(owner.read());
                int room = created.info().roomId();

                assertEquals(a.id().value(), created.option().ownerAccountId());
                assertEquals(a.id().value(), created.slots().getFirst().occupant().accountId());
                assertEquals(1001, created.option().ownerCharacterId());
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.send(RoomEntryFixtures.enter(room));
                var joined = wire.initialize(guest.read());

                assertEquals(created.option(), joined.option());
                assertEquals(b.id().value(), joined.slots().get(2).occupant().accountId());
                assertEquals(1002, joined.slots().get(2).occupant().characterId());
                wire.slot(owner.read());
                observer.read();
                observer.read();
                guest.sendFragmented(root("01-Ready.request"));

                assertArrayEquals(root("01-Ready.reply"), owner.read());
                assertArrayEquals(root("01-Ready.reply"), guest.read());
                guest.sendTogether(ready(1), ready(1));

                for (int repeat = 0; repeat < 2; repeat++) {
                    assertArrayEquals(root("01-Ready.reply"), owner.read());
                    assertArrayEquals(root("01-Ready.reply"), guest.read());
                }

                guest.sendTogether(team(1), ready(255));
                cash(guest);
                cash(owner);
                cash(observer);
                cash(foreign);
                observer.send(RoomEntryFixtures.enter(room));
                var init = wire.initialize(observer.read());
                wire.slot(owner.read());
                wire.slot(guest.read());

                assertEquals(3, init.info().details().playerCount());
                assertEquals(3, init.slots().get(2).state());
                assertEquals(b.id().value(), init.slots().get(2).occupant().accountId());
                assertEquals(1002, init.slots().get(2).occupant().characterId());
                guest.sendTogether(ready(0), team(1));

                assertArrayEquals(root("02-Ready.reply"), owner.read());
                assertArrayEquals(root("02-Ready.reply"), guest.read());
                assertArrayEquals(root("02-Ready.reply"), observer.read());
                var transfer = wire.changeTeam(owner.read());
                assertEquals(4, transfer.newSlot0());
                assertEquals(transfer, wire.changeTeam(guest.read()));
                assertEquals(transfer, wire.changeTeam(observer.read()));
                guest.send(ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                wire.ready(observer.read());
                observer.send(ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                wire.ready(observer.read());
                owner.send(ready(1));

                assertFalse(wire.ready(owner.read()).ready());
                cash(guest);
                cash(observer);
                owner.disconnect();

                assertEquals(0, wire.slot(observer.read()).slot().slotIndex());
                assertEquals(c.id().value(), wire.option(observer.read()).option().ownerAccountId());
                var reset = wire.ready(observer.read());
                assertEquals(c.id().value(), reset.accountId());
                assertFalse(reset.ready());
                wire.slot(guest.read());
                wire.option(guest.read());
                assertEquals(reset, wire.ready(guest.read()));
                observer.send(ready(0));

                assertEquals(reset, wire.ready(observer.read()));
                assertEquals(reset, wire.ready(guest.read()));
                guest.send(Arrays.copyOf(ready(0), 4));

                assertEquals(4, wire.slot(observer.read()).slot().slotIndex());
                cash(observer);
                cash(foreign);
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("bravo@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }

        try (var restarted = new Application(config)) {
            restarted.start();
            try (var owner = new CharacterTestClient(config.port())) {
                lobby(owner, a, 1);
                owner.send(root("create-unprotected"));
                var fresh = wire.initialize(owner.read());

                assertEquals(1, fresh.info().details().playerCount());
                assertEquals(2, fresh.slots().get(0).state());
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
        byte[] login = client.login(account.login().value());
        long localAccount = Integer.toUnsignedLong(ByteBuffer.wrap(login).order(ByteOrder.LITTLE_ENDIAN).getInt(5));

        assertEquals(account.id().value(), localAccount);

        long character = database.characters().findByAccount(account.id()).getFirst().id().value();
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        client.read();
        client.read();
        client.read();
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
    }
}

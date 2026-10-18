package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomCaptainIntegrationTest {
    @Test
    void runsBothCaptainModesOverTcpAndPublishesBusyAndWaitingRoomListings() throws Exception {
        try (var database = new CharacterTestDatabase()) {
            database.reset();
            database.execute("ALTER SEQUENCE characters_id_seq RESTART WITH 1001");
            var a = player(database, "alpha");
            var b = player(database, "bravo");
            var c = player(database, "observer");
            var config = database.configuration();
            var wire = new RoomResponseCodec();
            var lists = new LobbyRoomListCodec();

            try (var app = new Application(config); var logs = new LogCapture()) {
                app.start();
                try (
                    var owner = new CharacterTestClient(config.port());
                    var guest = new CharacterTestClient(config.port());
                    var observer = new CharacterTestClient(config.port())
                ) {
                    lobby(database, owner, a);
                    lobby(database, guest, b);
                    owner.read();
                    lobby(database, observer, c);
                    owner.read();
                    guest.read();
                    owner.send(root("create-unprotected"));
                    int room = wire.initialize(owner.read()).info().roomId();
                    guest.read();
                    guest.read();
                    observer.read();
                    observer.read();
                    guest.send(RoomEntryFixtures.enter(room));
                    wire.initialize(guest.read());
                    wire.slot(owner.read());
                    observer.read();
                    observer.read();
                    guest.send(RoomScenario.ready(1));
                    wire.ready(owner.read());
                    wire.ready(guest.read());

                    for (int mode : new int[]{2, 3}) {
                        owner.send(RoomScenario.settings(1, mode, mode == 2 ? 2 : 0, 255));
                        assertEquals(mode, wire.settings(owner.read()).gameType());
                        assertEquals(mode, wire.settings(guest.read()).gameType());
                        assertEquals(mode, lists.update(observer.read()).rooms().getFirst().gameType());
                        owner.sendFragmented(root("PqGameSession_ChiefDecision-native"));
                        wire.chiefDecision(owner.read());
                        wire.chiefDecision(guest.read());
                        assertEquals(2, lists.update(observer.read()).rooms().getFirst().state());
                        owner.sendTogether(root("PqGameSession_ChiefDecision-native"), SelectionFixtures.cash(0, 0));
                        assertArrayEquals(SelectionFixtures.cash(0, 0), owner.read());
                        owner.sendTogether(
                            root("PqGameSession_ChallengeChief-native"),
                            root("PqGameSession_CancelChief-native")
                        );
                        assertEquals(1001, wire.challengeChief(owner.read()).characterId());
                        assertEquals(1001, wire.cancelChief(owner.read()).characterId());
                        wire.challengeChief(guest.read());
                        wire.cancelChief(guest.read());
                        guest.send(integer(root("PqGameSession_ChallengeChief-native"), 4, 1002, 4));
                        assertEquals(1002, wire.challengeChief(guest.read()).characterId());
                        wire.challengeChief(owner.read());
                        owner.send(root("start-empty"));
                        assertEquals(
                            "The room is not ready to start.",
                            new NoticeMessageCodec().decode(owner.read()).message()
                        );
                        TimeUnit.MILLISECONDS.sleep(5100);
                        owner.send(root("PqGameSession_ChallengeChief-native"));
                        assertEquals(1001, wire.cancelChief(owner.read()).characterId());
                        owner.sendTogether(root("start-empty"), root("start-empty"));
                        assertEquals(
                            "Starting a match is currently unavailable.",
                            new NoticeMessageCodec().decode(owner.read()).message()
                        );
                        assertEquals(
                            "The room is not ready to start.",
                            new NoticeMessageCodec().decode(owner.read()).message()
                        );
                        assertEquals(
                            "Starting a match is currently unavailable.",
                            new NoticeMessageCodec().decode(guest.read()).message()
                        );
                        assertEquals(1, lists.update(observer.read()).rooms().getFirst().state());
                        cash(guest);
                        cash(observer);
                    }

                    assertTrue(logs.text().contains("room.captain.finalized"));
                    assertFalse(logs.text().contains("alpha@example.test"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                    assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                    owner.send(RoomScenario.weapon(1001, 200));
                    assertEquals(200, wire.changeWeapon(owner.read()).weaponId());
                    assertEquals(200, wire.changeWeapon(guest.read()).weaponId());
                }
            }
        }
    }

    private Account player(CharacterTestDatabase database, String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        assertFalse(database.create(account.id(), "Hero" + name, AccountGender.FEMALE).catalog().isEmpty());
        return account;
    }

    private void lobby(CharacterTestDatabase database, CharacterTestClient client, Account account) throws Exception {
        client.login(account.login().value());
        var character = database.characters().findByAccount(account.id()).getFirst();
        client.send(CharacterFixtures.select(character.id().value()));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(1));
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

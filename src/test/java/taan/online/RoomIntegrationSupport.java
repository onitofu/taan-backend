package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import taan.online.account.model.Account;

public final class RoomIntegrationSupport {
    private RoomIntegrationSupport() {
    }

    public static void enterLobby(
        CharacterTestDatabase database,
        CharacterTestClient client,
        Account account,
        int channel
    ) throws Exception {
        client.login(account.login().value());
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

    public static void assertBalances(CharacterTestClient client, long cash, long gold) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(cash, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(gold), client.read());
    }

    public static void assertBalances(
        CharacterTestDatabase database,
        CharacterTestClient client,
        Account account
    ) throws Exception {
        long cash = database.scalar("SELECT cash FROM accounts WHERE id = " + account.id().value());
        long gold = database.characters().findByAccount(account.id()).getFirst().gold();
        assertBalances(client, cash, gold);
    }
}

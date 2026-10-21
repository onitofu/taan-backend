package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.character.repository.jdbc.CharacterPersistenceException;
import taan.online.session.protocol.codec.UpdateCashCodec;

class BalanceRefreshConnectionTest {
    @Test
    void deliversBothActualBalancesInOrderAndKeepsTheConnectionOpen() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            select(harness);
            var repository = harness.characters();
            var original = repository.stored.getFirst();
            repository.stored.set(0, withGold(original, 789));
            repository.cash = 456;
            harness.channel().writeInbound(SelectionFixtures.cash(0xffff_ffffL, 0));
            harness.run();

            assertEquals(456, new UpdateCashCodec().decode(read(harness)).balance());
            assertEquals(789, new UpdateMoneyCodec().decode(read(harness)).balance());
            assertNull(harness.channel().readOutbound());
            assertTrue(harness.channel().isActive());

            repository.cash = 12;
            repository.stored.set(0, withGold(original, 34));
            harness.channel().writeInbound(SelectionFixtures.cash(999, 0));
            harness.run();

            assertEquals(12, new UpdateCashCodec().decode(read(harness)).balance());
            assertEquals(34, new UpdateMoneyCodec().decode(read(harness)).balance());
            assertTrue(harness.channel().isActive());
        }
    }

    @Test
    void classifiesDatabaseFailureOnceWithoutLeakingBalancesOrSqlValues() {
        try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
            select(harness);
            harness.characters().failure = new CharacterPersistenceException(
                "findBalances",
                new SQLException("private-balance-value")
            );
            harness.channel().writeInbound(SelectionFixtures.cash(0, 0));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertEquals(1, logs.events().stream().filter(e -> e.getLevel().toString().equals("ERROR")).count());
            assertTrue(logs.text().contains("session.balances.refresh.failed"));
            assertFalse(logs.text().contains("private-balance-value"));
        }
    }

    private void select(ConnectionContextTestHarness harness) {
        harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
        harness.run();
        read(harness);
        harness.repository().exists(true);
        harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
        harness.run();
        read(harness);
        harness.characters().stored.add(
            CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma")
        );
        harness.channel().writeInbound(CharacterFixtures.select(1001));
        harness.run();
        read(harness);
        SelectionFixtures.assertPlayableTime(read(harness));
    }

    private byte[] read(ConnectionContextTestHarness harness) {
        ByteBuf buffer = harness.channel().readOutbound();
        byte[] root = new byte[buffer.readIntLE()];
        buffer.readBytes(root);
        buffer.release();
        return root;
    }

    private GameCharacter withGold(GameCharacter original, long gold) {
        return new GameCharacter(
            original.id(),
            original.accountId(),
            original.name(),
            original.level(),
            original.gender(),
            original.appearance(),
            original.createdAt(),
            original.progress(),
            gold
        );
    }
}

package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.channel.service.GetChannelListAction;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.repository.jdbc.CharacterPersistenceException;
import taan.online.character.service.SelectCharacterAction;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.protocol.codec.UpdateCashCodec;
import taan.online.session.service.RefreshBalancesAction;
import taan.online.session.service.SessionCharacterAccess;
import taan.online.session.service.SessionRegistry;

class SelectionConnectionTest {
    @Test
    void deliversSelectionBeforeQueuedChannelAndCashRequestsAndIgnoresRepeatedSelection() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            login(harness);
            harness.channel().writeInbound(CharacterFixtures.select(1001));
            harness.channel().writeInbound(SelectionFixtures.channels());

            assertEquals(1, harness.jobs().size());

            harness.run();
            SelectionFixtures.assertSelection(read(harness), 1001, AccountGender.FEMALE);
            SelectionFixtures.assertPlayableTime(read(harness));

            assertEquals(1, harness.jobs().size());

            harness.run();
            SelectionFixtures.assertChannels(read(harness), 2181);
            harness.channel().writeInbound(new byte[]{(byte) 0xae, 6, (byte) 0xbb, 0x3a});

            assertTrue(harness.jobs().isEmpty());

            harness.channel().writeInbound(SelectionFixtures.cash(1000, 0));
            harness.run();

            assertEquals(0, new UpdateCashCodec().decode(read(harness)).balance());
            assertEquals(0, new UpdateMoneyCodec().decode(read(harness)).balance());

            harness.channel().writeInbound(SelectionFixtures.logging(1001));
            harness.run();

            assertNull(harness.channel().readOutbound());
            assertTrue(harness.channel().isActive());
        }
    }

    @Test
    void closesRejectedAndFailedSelectionsWithNoSuccessPacketAndOneSafeError() {
        try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
            login(harness);
            harness.channel().writeInbound(CharacterFixtures.select(999));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertTrue(logs.text().contains("character.selection.rejected"));
            assertFalse(logs.text().contains("character.selection.failed"));
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            login(harness);
            harness.characters().failure = new CharacterPersistenceException(
                "select",
                new SQLException(
                    "Luma private-value"
                )
            );

            try (var logs = new LogCapture()) {
                harness.channel().writeInbound(CharacterFixtures.select(1001));
                harness.run();

                assertFalse(harness.channel().isActive());
                assertNull(harness.channel().readOutbound());
                assertEquals(
                    1,
                    logs.events().stream().filter(
                        event -> event.getKeyValuePairs().stream().anyMatch(
                            field -> field.key.equals("event") && field.value.equals("character.selection.failed")
                        )
                    ).count()
                );
                assertFalse(logs.text().contains("Luma"));
                assertFalse(logs.text().contains("private-value"));
            }
        }

        assertEquals(
            CharacterLogEvent.SELECTION_FAILED,
            SelectedCharacterRequestDispatcher.failureEvent(0x3abb06ae)
        );
        assertEquals(
            ChannelLogEvent.LIST_FAILED,
            SelectedCharacterRequestDispatcher.failureEvent(0x331d0636)
        );
    }

    @Test
    void cancelsQueuedSelectionAndBackgroundWorkAndDropsInvalidatedResults() {
        for (int operation = 0; operation < 4; operation++) {
            for (boolean invalidate : new boolean[]{false, true}) {
                try (var harness = new ConnectionContextTestHarness(8)) {
                    login(harness);

                    if (operation != 0) {
                        harness.channel().writeInbound(CharacterFixtures.select(1001));
                        harness.run();
                        read(harness);
                        SelectionFixtures.assertPlayableTime(read(harness));
                    }

                    byte[] root = List.of(
                        CharacterFixtures.select(1001),
                        SelectionFixtures.channels(),
                        SelectionFixtures.cash(0, 0),
                        SelectionFixtures.logging(1001)
                    ).get(operation);
                    harness.channel().writeInbound(root);

                    assertEquals(1, harness.jobs().size());

                    if (invalidate) {
                        harness.invalidateSession();
                        harness.run();

                        assertNull(harness.channel().readOutbound());
                        assertTrue(harness.channel().isActive());
                    } else {
                        harness.disconnect(false);

                        assertTrue(harness.jobs().isEmpty());
                        assertFalse(harness.channel().isActive());
                    }
                }
            }
        }
    }

    @Test
    void discardsSelectionWhenTheConnectionClosesWhileReadingInventories() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            login(harness);
            harness.characters().onInventories = () -> harness.channel().unsafe().close(harness.channel().newPromise());
            harness.channel().writeInbound(CharacterFixtures.select(1001));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
        }
    }

    @Test
    void identifiesEveryBootstrapPacketAndLogsUnexpectedChannelFailureOnceWithoutExternalValues() {
        for (int type : new int[]{0x3abb06ae, 0x331d0636, 0x1d380495, 0x10b3037a}) {
            assertTrue(SelectedCharacterRequestDispatcher.accepts(type));
        }

        assertFalse(SelectedCharacterRequestDispatcher.accepts(0));

        var sessions = new SessionRegistry();
        var access = new SessionCharacterAccess(sessions);
        var characters = new TestCharacterRepository();
        characters.stored.add(CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma"));
        characters.inventoryData = SelectionFixtures.inventories(AccountGender.FEMALE);
        SelectedCharacterAccess broken = ignored -> {
            throw new IllegalStateException("private-channel-value");
        };
        var dispatcher = new SelectedCharacterRequestDispatcher(
            new SelectCharacterPacketHandler(
                new SelectCharacterAction(
                    access,
                    characters,
                    InventoryFixtures.catalog()
                ),
                access,
                TestLevels.table()
            ),
            new GetChannelListPacketHandler(
                new GetChannelListAction(broken, SelectionFixtures.registry(broken)),
                broken
            ),
            new CashRefreshPacketHandler(sessions, new RefreshBalancesAction(sessions, characters)),
            new ClientLoggingPacketHandler(sessions),
            3600
        );

        try (var harness = new ConnectionContextTestHarness(8, sessions, true, Clock.systemUTC(), dispatcher);
            var logs = new LogCapture()) {
            login(harness);
            harness.channel().writeInbound(CharacterFixtures.select(1001));
            harness.run();
            read(harness);
            SelectionFixtures.assertPlayableTime(read(harness));
            harness.channel().writeInbound(SelectionFixtures.channels());
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertTrue(logs.text().contains("channel.list.failed"));
            assertFalse(logs.text().contains("private-channel-value"));
            assertFalse(logs.text().contains("connection.failed"));
        }
    }

    private void login(ConnectionContextTestHarness harness) {
        harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
        harness.run();
        read(harness);
        harness.repository().exists(true);
        harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
        harness.run();
        read(harness);
        harness.characters().stored.add(
            CharacterFixtures.character(
                1001,
                new AccountId(1),
                AccountGender.FEMALE,
                "Luma"
            )
        );
    }

    private byte[] read(ConnectionContextTestHarness harness) {
        ByteBuf buffer = harness.channel().readOutbound();
        int size = buffer.readIntLE();
        byte[] root = new byte[size];
        buffer.readBytes(root);
        buffer.release();
        return root;
    }
}

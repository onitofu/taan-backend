package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.session.service.SessionRegistry;

class InventoryConnectionTest {
    @Test
    void ignoresClosedRequestsAndReleasesQueuedWorkWithoutTouchingStorage() {
        var output = new CollectingPacketOutput();
        var repository = new TestInventoryRepository();
        var dispatcher = InventoryFixtures.dispatcher(new SessionRegistry(), repository);

        assertEquals(
            ProtocolEvent.INVENTORY_REQUEST_IGNORED,
            dispatcher.dispatch(
                UUID.randomUUID(),
                new byte[0],
                output
            ).event()
        );
        assertTrue(output.batches.getFirst().isEmpty());
        assertEquals(0, repository.begins);

        try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
            select(harness);
            harness.channel().writeInbound(request(harness));
            harness.disconnect(false);

            assertTrue(harness.jobs().isEmpty());
            assertEquals(0, harness.inventories().begins);
            assertTrue(logs.text().contains("inventory.request.cancelled"));
        }
    }

    @Test
    void logsUnexpectedStorageFailureOnceAndClosesWithoutLeakingSensitiveValues() {
        try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
            select(harness);
            harness.inventories().failure = new IllegalStateException(
                "PrivateInventoryValue " + TestFixtures.CREDENTIAL
            );
            harness.channel().writeInbound(request(harness));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertEquals(
                1,
                logs.events().stream().filter(
                    entry -> entry.getKeyValuePairs().stream()
                        .anyMatch(field -> field.key.equals("event") && field.value.equals("inventory.move.failed"))
                ).count()
            );
            assertFalse(logs.text().contains("PrivateInventoryValue"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    private byte[] request(ConnectionContextTestHarness harness) {
        var move = InventoryFixtures.command(harness.inventories()).move();
        return InventoryFixtures.move(
            1,
            move.sourceInventoryId(),
            move.instanceId(),
            move.sourceSlot(),
            move.sourceItemId(),
            move.destinationInventoryId(),
            move.destinationSlot(),
            move.destinationObservedItemId()
        );
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
            CharacterFixtures.character(
                1001,
                new AccountId(
                    1
                ),
                AccountGender.FEMALE,
                "Luma"
            )
        );
        harness.channel().writeInbound(CharacterFixtures.select(1001));
        harness.run();

        assertEquals(0x3acb06af, ChannelFixtures.type(read(harness)));
        SelectionFixtures.assertPlayableTime(read(harness));
    }

    private byte[] read(ConnectionContextTestHarness harness) {
        ByteBuf buffer = harness.channel().readOutbound();
        var bytes = new byte[buffer.readIntLE()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}

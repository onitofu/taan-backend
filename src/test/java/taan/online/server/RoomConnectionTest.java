package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.leave;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomConnectionTest {
    @Test
    void createsLeavesReplaysAndCreatesAgainWhileLobbyUnsubscribeKeepsTheRoomPhase() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8)) {
            lobby(harness);

            harness.channel().writeInbound(root("create-unprotected"));
            harness.run();
            var first = new RoomResponseCodec().initialize(read(harness));

            assertEquals(1, first.info().roomId());
            harness.channel().writeInbound(ChannelFixtures.leave());
            harness.run();

            assertEquals(0x22910506, ChannelFixtures.type(read(harness)));
            harness.channel().writeInbound(root("start-empty"));
            harness.run();

            assertEquals(
                "Starting a match is currently unavailable.",
                new NoticeMessageCodec().decode(read(harness)).message()
            );
            harness.channel().writeInbound(root("create-unprotected"));
            harness.run();

            assertEquals(0, new RoomResponseCodec().createFail(read(harness)).reason());
            harness.channel().writeInbound(leave(999, 0));
            harness.run();

            assertEquals(0, new RoomResponseCodec().leave(read(harness)).success());
            harness.channel().writeInbound(leave(first.info().roomId(), 0));
            harness.run();

            assertEquals(1, new RoomResponseCodec().leave(read(harness)).success());
            harness.channel().writeInbound(leave(first.info().roomId(), 0));
            harness.run();

            assertEquals(1, new RoomResponseCodec().leave(read(harness)).success());
            harness.channel().writeInbound(ChannelFixtures.enter(0));
            harness.run();
            drain(harness);
            harness.channel().writeInbound(root("create-unprotected"));
            harness.run();

            assertEquals(2, new RoomResponseCodec().initialize(read(harness)).info().roomId());
            harness.channel().writeInbound(leave(first.info().roomId(), 0));
            harness.run();

            assertEquals(1, new RoomResponseCodec().leave(read(harness)).success());
            harness.channel().writeInbound(root("start-empty"));
            harness.run();

            assertEquals(0x18dd043c, ChannelFixtures.type(read(harness)));
            assertTrue(harness.channel().isActive());
        }
    }

    @Test
    void releasesRoomsAndChannelSeatsOnDisconnectTimeoutAndFailedOrDelayedWrites() throws Exception {
        for (String mode : List.of("DISCONNECT", "IDLE", "WRITE_FAILURE", "WRITE_TIMEOUT")) {
            try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
                lobby(harness);

                var delay = new DelayedOutboundWrite();

                if (mode.startsWith("WRITE")) {
                    harness.channel().pipeline().addBefore(harness.context().name(), "delayed", delay);
                }

                harness.channel().writeInbound(root("create-unprotected"));
                harness.run();

                if (mode.equals("DISCONNECT")) {
                    read(harness);
                    harness.disconnect(false);
                } else if (mode.equals("IDLE")) {
                    read(harness);
                    harness.channel().advanceTimeBy(301, TimeUnit.SECONDS);
                    harness.channel().runScheduledPendingTasks();
                } else if (mode.equals("WRITE_FAILURE")) {
                    delay.fail();
                    harness.channel().runPendingTasks();
                } else {
                    harness.disconnect(true);
                    delay.succeed();
                    harness.channel().runPendingTasks();
                }

                assertFalse(harness.channel().isActive(), mode);
                assertEquals(0, harness.channels.catalog().fills().get(1));
                assertEquals(
                    1,
                    logs.events().stream().filter(
                        event -> event.getKeyValuePairs().stream().anyMatch(
                            field -> field.key.equals("event") && field.value.equals("room.destroyed")
                        )
                    ).count()
                );
                assertNull(harness.channel().readOutbound());
            }
        }
    }

    @Test
    void cancelsQueuedRoomRequestsAndIgnoresLateResultsOfInvalidatedSessions() throws Exception {
        for (byte[] request : List.of(
            root("create-unprotected"),
            root("enter-empty-password"),
            leave(1, 0),
            root("start-empty")
        )) {
            try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
                lobby(harness);

                harness.channel().writeInbound(root("create-unprotected"));
                harness.run();
                read(harness);
                harness.channel().writeInbound(request);
                harness.disconnect(false);

                assertTrue(harness.jobs().isEmpty());
                assertTrue(logs.text().contains("room.request.cancelled"));
            }
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            lobby(harness);

            harness.characters().onCommit = harness::invalidateSession;
            harness.channel().writeInbound(root("create-unprotected"));
            harness.run();

            assertNull(harness.channel().readOutbound());
            assertTrue(harness.channel().isActive());
        }
    }

    @Test
    void logsUnexpectedRoomFailuresOnceWithoutPrivateValues() throws Exception {
        for (boolean creating : new boolean[]{true, false}) {
            try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
                lobby(harness);

                if (creating) {
                    harness.characters().failure = new IllegalStateException("Private title and credentials");
                } else {
                    harness.channel().writeInbound(root("create-unprotected"));
                    harness.run();
                    read(harness);
                    harness.rooms.close();
                }

                harness.channel().writeInbound(root(creating ? "create-unprotected" : "start-empty"));
                harness.run();

                assertFalse(harness.channel().isActive());
                String event = creating ? "room.creation.failed" : "room.operation.failed";
                assertEquals(
                    1,
                    logs.events().stream().filter(
                        entry -> entry.getKeyValuePairs().stream().anyMatch(
                            field -> field.key.equals("event") && field.value.equals(event)
                        )
                    ).count()
                );
                assertFalse(logs.text().contains("Private title and credentials"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("Ngon vào đây"));
            }
        }
    }

    private void lobby(ConnectionContextTestHarness harness) {
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
        drain(harness);
        harness.channel().writeInbound(ChannelFixtures.move(1));
        harness.run();
        drain(harness);
        harness.channel().writeInbound(ChannelFixtures.enter(1));
        harness.run();
        drain(harness);
    }

    private byte[] read(ConnectionContextTestHarness harness) {
        ByteBuf buffer = harness.channel().readOutbound();
        byte[] root = new byte[buffer.readIntLE()];
        buffer.readBytes(root);
        buffer.release();
        return root;
    }

    private void drain(ConnectionContextTestHarness harness) {
        while (harness.channel().outboundMessages().size() > 0) {
            read(harness);
        }
    }
}

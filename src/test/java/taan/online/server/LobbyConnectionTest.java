package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.controller.MoveToChannelPacketHandler;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.ChannelTestAccess;
import taan.online.channel.service.MoveToChannelAction;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.lobby.controller.LobbyPacketHandler;
import taan.online.lobby.service.EnterLobbyAction;
import taan.online.lobby.service.LeaveLobbyAction;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionRegistry;

class LobbyConnectionTest {
    @Test
    void mapsUnboundLeaveAndClosedRequestsWithoutSendingInventedPackets() {
        var access = new ChannelTestAccess();
        var id = access.add(1, 1);
        var registry = SelectionFixtures.registry(access);
        var dispatcher = dispatcher(registry, access);
        var output = new CollectingPacketOutput();

        assertEquals(
            ProtocolEvent.LOBBY_LEFT_UNBOUND,
            dispatcher.dispatch(0x22850505, id, ChannelFixtures.leave(), output).event()
        );
        assertEquals(0x22910506, ChannelFixtures.type(output.batches.getFirst().getFirst()));

        access.players.clear();

        assertEquals(
            ProtocolEvent.CHANNEL_REQUEST_IGNORED,
            dispatcher.dispatch(0x33c00633, id, new byte[0], output).event()
        );
        assertEquals(
            ProtocolEvent.CHANNEL_REQUEST_IGNORED,
            dispatcher.dispatch(0x22aa0516, id, new byte[0], output).event()
        );
        assertEquals(1, output.batches.size());
    }

    @Test
    void logsUnexpectedChannelAndLobbyFailuresOnceAndCancelsQueuedCommandsWithoutLeakingValues() {
        for (byte[] request : List.of(ChannelFixtures.move(1), ChannelFixtures.enter(1), ChannelFixtures.leave())) {
            var sessions = new SessionRegistry();
            SelectedCharacterAccess broken = id -> {
                throw new IllegalStateException("PrivateActorValue");
            };
            var registry = SelectionFixtures.registry(broken);
            var dispatcher = dispatcher(registry, broken);

            try (var harness = new ConnectionContextTestHarness(8, sessions, true, Clock.systemUTC(), null, dispatcher);
                var logs = new LogCapture()) {
                select(harness);
                harness.channel().writeInbound(request);
                harness.run();

                assertFalse(harness.channel().isActive());
                assertNull(harness.channel().readOutbound());
                String event = ChannelFixtures.type(
                    request
                ) == 0x33c00633 ? "channel.move.failed" : "lobby.request.failed";

                assertEquals(
                    1,
                    logs.events().stream().filter(
                        entry -> entry.getKeyValuePairs().stream()
                            .anyMatch(field -> field.key.equals("event") && field.value.equals(event))
                    ).count()
                );
                assertFalse(logs.text().contains("PrivateActorValue"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }

            try (var harness = new ConnectionContextTestHarness(8)) {
                select(harness);
                harness.channel().writeInbound(request);
                harness.disconnect(false);

                assertTrue(harness.jobs().isEmpty());
                assertFalse(harness.channel().isActive());
            }
        }
    }

    @Test
    void freesTheReservedSeatWhenTheMoveResponseCannotFlushBeforeTheRequestDeadline() {
        var sessions = new SessionRegistry();
        var access = new SessionChannelAccess(sessions);
        var registry = SelectionFixtures.registry(access);

        try (var harness = new ConnectionContextTestHarness(
            8,
            sessions,
            true,
            Clock.systemUTC(),
            null,
            dispatcher(registry, access)
        )) {
            select(harness);
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore(harness.context().name(), "delayed", delay);
            harness.channel().writeInbound(ChannelFixtures.move(1));
            harness.run();

            assertEquals(1, registry.catalog().fills().get(1));
            assertNull(harness.channel().readOutbound());

            harness.disconnect(true);

            assertFalse(harness.channel().isActive());
            assertEquals(0, registry.catalog().fills().get(1));

            delay.succeed();
            harness.channel().runPendingTasks();

            assertNull(harness.channel().readOutbound());
        }
    }

    @Test
    void ignoresAdditionalTransportFailuresWhileClosureIsAlreadyPending() {
        try (var harness = new ConnectionContextTestHarness(8); var logs = new LogCapture()) {
            var delay = new DelayedChannelClose();
            harness.channel().pipeline().addBefore(harness.context().name(), "close", delay);
            harness.handler().exceptionCaught(harness.context(), new MalformedPacketException("INVALID"));
            harness.handler().exceptionCaught(harness.context(), new IOException("PrivateTransportValue"));
            harness.handler().channelRead0(harness.context(), new byte[0]);

            assertEquals(1, delay.calls);
            assertTrue(harness.channel().isActive());
            assertFalse(logs.text().contains("PrivateTransportValue"));

            delay.finish();
            harness.channel().runPendingTasks();

            assertFalse(harness.channel().isActive());
        }
    }

    @Test
    void closesSafelyIfTransportInitializationFailsBeforeTheGreeting() {
        try (var harness = new ConnectionContextTestHarness(8, new SessionRegistry(), false, Clock.systemUTC())) {
            harness.handler().exceptionCaught(harness.context(), new MalformedPacketException("EARLY_FAILURE"));
            harness.channel().runPendingTasks();

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertNull(harness.channel().readOutbound());
        }
    }

    private LobbyRequestDispatcher dispatcher(ChannelRegistry registry, SelectedCharacterAccess access) {
        return new LobbyRequestDispatcher(
            new MoveToChannelPacketHandler(
                new MoveToChannelAction(registry),
                access,
                2181
            ),
            new LobbyPacketHandler(new EnterLobbyAction(registry), new LeaveLobbyAction(registry), access)
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
                new AccountId(1),
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

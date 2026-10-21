package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchResultAssertions;
import taan.online.MatchFixtures;
import taan.online.LogCapture;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.model.RoomMatchToken;

class MatchConnectionTest {
    @Test
    void deliversOneFullResultBeforeRoomUpdatesAndConsumesCardsWithoutReply() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = populate(world);
            var owner = clients.get(0);
            var guest = clients.get(1);
            var token = start(world, MatchPhase.PREVIEW);
            world.request(owner, MatchFixtures.root("give-up-empty"));

            for (var client : clients) {
                MatchResultAssertions.readEnd(() -> read(client), 0);
                assertEquals(false, new RoomResponseCodec().ready(read(client)).ready());
                assertNull(client.channel().readOutbound());
                assertTrue(client.channel().isActive());
            }

            assertTrue(world.matches.current(token).isEmpty());
            world.request(owner, MatchFixtures.root("PqResult_RandSelect.captured"));
            world.request(owner, MatchFixtures.root("PqResult_SelectCard"));
            world.request(owner, MatchFixtures.root("give-up-empty"));

            assertNull(owner.channel().readOutbound());
            world.request(owner, SelectionFixtures.cash(0, 0));
            assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
            owner.channel().writeInbound(MatchFixtures.root("give-up-empty"));
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            var next = start(world, MatchPhase.PREVIEW);
            owner.run();
            world.tick();

            assertTrue(world.matches.current(next).isPresent());
            assertNull(owner.channel().readOutbound());
            assertNull(guest.channel().readOutbound());
        }
    }

    @Test
    void disconnectAndActiveLeaveCloseLoadingPeersButReturnLoadedSurvivors() throws Exception {
        for (var phase : new MatchPhase[]{MatchPhase.LOADING, MatchPhase.PREVIEW}) {
            for (boolean leave : new boolean[]{false, true}) {
                try (var world = new RoomConnectionWorld()) {
                    var clients = populate(world);
                    var owner = clients.get(0);
                    var guest = clients.get(1);
                    var token = start(world, phase);

                    if (leave) {
                        world.request(owner, RoomScenario.leave(1, 0));
                    } else {
                        owner.disconnect(false);
                    }

                    world.tick();
                    world.tick();

                    assertFalse(owner.channel().isActive());
                    assertTrue(world.matches.current(token).isEmpty());
                    assertNull(owner.channel().readOutbound());

                    if (phase == MatchPhase.LOADING) {
                        assertFalse(guest.channel().isActive());
                        assertTrue(world.rooms.forChannel(1).isEmpty());
                    } else {
                        assertTrue(guest.channel().isActive());
                        MatchResultAssertions.readEnd(() -> read(guest), 0);
                        var wire = new RoomResponseCodec();
                        wire.ready(read(guest));
                        assertEquals(0, wire.slot(read(guest)).slot().slotIndex());
                        wire.option(read(guest));
                        assertNull(guest.channel().readOutbound());
                        var room = RuntimeRoomTestAccess.room(world.rooms, 1);

                        assertEquals(1002, room.owner().character().id().value());
                        var fresh = world.client(1);
                        assertTrue(fresh.channel().isActive());
                    }
                }
            }
        }
    }

    @Test
    void cancelsQueuedGiveUpBeforeDisconnectAndClosesMalformedRequests() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = populate(world);
            var owner = clients.get(0);
            var guest = clients.get(1);
            start(world, MatchPhase.PREVIEW);
            owner.channel().writeInbound(MatchFixtures.root("give-up-empty"));

            assertEquals(1, owner.jobs().size());
            owner.disconnect(false);
            world.tick();

            assertTrue(owner.jobs().isEmpty());
            MatchResultAssertions.readEnd(() -> read(guest), 0);
            drain(guest);
            world.request(guest, new byte[]{0x72, 0x06, (byte) 0x86, 0x38, 0});

            assertFalse(guest.channel().isActive());
        }
    }

    @Test
    void closesOnlyTheBrokenEndGameRecipientAndLeavesTheOtherResultUsable() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = populate(world);
            var owner = clients.get(0);
            var guest = clients.get(1);
            start(world, MatchPhase.PREVIEW);
            var delayed = new QueuedOutboundWrites();
            owner.channel().pipeline().addBefore(owner.context().name(), "delay", delayed);
            world.request(guest, MatchFixtures.root("give-up-empty"));

            MatchResultAssertions.readEnd(() -> read(guest), 0);
            new RoomResponseCodec().ready(read(guest));
            delayed.fail();
            delayed.fail();
            world.tick();

            assertFalse(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            new RoomResponseCodec().slot(read(guest));
            new RoomResponseCodec().option(read(guest));
            world.request(guest, MatchFixtures.root("PqResult_RandSelect.captured"));

            assertNull(guest.channel().readOutbound());
        }
    }

    @Test
    void rejectsKnownMatchPacketsOutsideTheRoom() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(MatchFixtures.root("give-up-empty"));

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
        }
    }

    @Test
    void containsAnUnexpectedDispatchFailureAndCleansUpTheRequestersMatch() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var matches = world.matches;
            var dispatcher = new MatchRequestDispatcher(
                new MatchPacketHandler(
                    matches,
                    new TerminateMatchAction(null),
                    new ConsumeMatchResultAction(matches),
                    new ConfirmMatchLoadingAction(matches),
                    new ConfirmMatchPositionAction(matches),
                    new ReportMatchEnemiesAction(matches)
                )
            );
            var owner = world.client(1, dispatcher);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            var token = start(world, MatchPhase.PREVIEW);
            world.request(owner, MatchFixtures.root("give-up-empty"));

            assertFalse(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            assertTrue(matches.current(token).isEmpty());
            MatchResultAssertions.readEnd(() -> read(guest), 0);
            assertTrue(logs.text().contains("match.operation.failed"));
            assertEquals(
                1,
                logs.events().stream().filter(event -> event.getLevel().toString().equals("ERROR")).count()
            );
        }
    }

    @Test
    void leavesAnUnsentReservationUsingTheOrdinaryRoomReply() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = populate(world);
            var owner = clients.getFirst();
            var guest = clients.get(1);
            var room = RuntimeRoomTestAccess.room(world.rooms, 1);
            var token = world.rooms.reserveMatch(room.ownerSessionId()).token();
            world.request(owner, RoomScenario.leave(1, 0));

            assertFalse(new RoomResponseCodec().ready(read(owner)).ready());
            assertEquals(1, new RoomResponseCodec().leave(read(owner)).success());
            assertNull(owner.channel().readOutbound());
            assertTrue(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            assertTrue(world.matches.current(token).isEmpty());
            assertTrue(world.rooms.membership(room.ownerSessionId()).isEmpty());
        }
    }

    private List<ConnectionContextTestHarness> populate(RoomConnectionWorld world) throws Exception {
        var owner = world.client(1);
        var guest = world.client(2);
        drain(owner);
        int room = world.create(owner);
        drain(guest);
        world.join(guest, room);
        drain(owner);
        world.request(guest, RoomScenario.ready(1));
        drain(owner);
        drain(guest);
        return List.of(owner, guest);
    }

    private RoomMatchToken start(RoomConnectionWorld world, MatchPhase phase) throws Exception {
        var room = RuntimeRoomTestAccess.room(world.rooms, 1);
        var token = world.rooms.reserveMatch(room.ownerSessionId()).token();
        world.matches.activate(token, world.rooms.matchCallbacks());
        world.rooms.commitMatch(token).toCompletableFuture().join();

        if (phase == MatchPhase.PREVIEW) {
            MatchFixtures.loaded(world.matches, token);
        }

        world.drainOutputs();
        return token;
    }
}

package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.RecordingRoomOutput;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.lobby.service.ListLobbyRoomsAction;

class RoomListingPublicationTest {
    @Test
    void publishesImmutableSortedChannelSnapshotsAndUnsubscribesTheCreatorImmediately() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 2);
            var c = scenario.player(3, 1);
            var old = scenario.rooms.forChannel(1);
            var first = scenario.create.execute(a, RoomScenario.command(), new RecordingRoomOutput()).room();
            var other = scenario.create.execute(b, RoomScenario.command(), new RecordingRoomOutput()).room();
            var last = scenario.create.execute(c, RoomScenario.command(), new RecordingRoomOutput()).room();
            var snapshot = scenario.rooms.forChannel(1);

            assertTrue(old.isEmpty());
            assertEquals(List.of(first.id(), last.id()), snapshot.stream().map(value -> value.id()).toList());
            assertEquals(List.of(other.id()), scenario.rooms.forChannel(2).stream().map(value -> value.id()).toList());
            assertTrue(scenario.rooms.forChannel(99).isEmpty());
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            assertEquals(ChannelPresencePhase.MEMBER, scenario.channels.membership(a).orElseThrow().phase());
            assertTrue(new ListLobbyRoomsAction(scenario.channels, scenario.rooms).snapshot(a).isEmpty());
            scenario.rooms.release(a, "DISCONNECT");

            assertEquals(2, snapshot.size());
            assertEquals(List.of(last.id()), scenario.rooms.forChannel(1).stream().map(value -> value.id()).toList());
            scenario.rooms.close();

            assertTrue(scenario.rooms.forChannel(1).isEmpty());
            assertTrue(scenario.rooms.forChannel(2).isEmpty());
        }
    }

    @Test
    void allowsLockFreeReadsWhileTheRoomOwnerIsBusy() throws Exception {
        try (var scenario = new RoomScenario(); var worker = Executors.newSingleThreadExecutor()) {
            var id = scenario.player(1, 1);
            scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var owner = worker.submit(
                () -> {
                    synchronized (scenario.rooms) {
                        locked.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Room owner was not released");
                        }
                    }
                    return true;
                }
            );

            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                assertEquals(1, scenario.rooms.forChannel(1).size());
            } finally {
                release.countDown();
            }

            assertTrue(owner.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void doesNotLoseCreationOrDeletionRacingWithLobbyEntry() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var creator = scenario.player(1, 1);
                    var observer = scenario.player(2, 1);
                    scenario.channels.hideFromLobby(observer);
                    var out = new CollectingPacketOutput();
                    var view = RoomListingFixtures.view(observer, scenario.channels, scenario.rooms, out);
                    var barrier = new CyclicBarrier(2);
                    var enter = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.channels.enter(observer, view);
                        }
                    );
                    var create = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.create.execute(creator, RoomScenario.command(), new RecordingRoomOutput());
                        }
                    );
                    enter.get(5, TimeUnit.SECONDS);
                    var room = create.get(5, TimeUnit.SECONDS).room();
                    var packets = out.batches.stream().flatMap(List::stream).filter(
                        packet -> Set.of(0x500c07d7, 0x611208a6).contains(ChannelFixtures.type(packet))
                    ).toList();

                    assertEquals(0x500c07d7, ChannelFixtures.type(packets.getFirst()));
                    assertTrue(packets.stream().anyMatch(packet -> !list(packet).isEmpty()));
                    scenario.channels.hideFromLobby(observer);
                    out.batches.clear();
                    var deletionBarrier = new CyclicBarrier(2);
                    var reenter = workers.submit(
                        () -> {
                            deletionBarrier.await(5, TimeUnit.SECONDS);
                            return scenario.channels.enter(observer, view);
                        }
                    );
                    var remove = workers.submit(
                        () -> {
                            deletionBarrier.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(creator, "DISCONNECT");
                            return true;
                        }
                    );
                    reenter.get(5, TimeUnit.SECONDS);
                    remove.get(5, TimeUnit.SECONDS);
                    var snapshots = out.batches.stream().flatMap(List::stream)
                        .filter(packet -> ChannelFixtures.type(packet) == 0x500c07d7).toList();

                    assertTrue(list(snapshots.getLast()).isEmpty());
                    assertTrue(scenario.rooms.forChannel(room.channelId()).isEmpty());
                }
            }
        }
    }

    private List<Integer> list(byte[] packet) {
        var codec = new LobbyRoomListCodec();
        var rooms = ChannelFixtures.type(packet) == 0x500c07d7
            ? codec.initialize(packet).rooms() : codec.update(packet).rooms();
        return rooms.stream().map(value -> value.roomId()).toList();
    }
}

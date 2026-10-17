package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.RoomEntryTarget;
import taan.online.session.model.SessionId;

class RoomCleanupConcurrencyTest {
    @Test
    void serializesOrdinaryLeaveAgainstDisconnectAndPublishesOnlyOneHandoff() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
                    var a = scenario.player(1, 1);
                    var b = scenario.player(2, 1);
                    int room = scenario.create.execute(
                        a,
                        RoomScenario.command(),
                        new RecordingRoomOutput()
                    ).room().id().value();
                    var out = new RecordingRoomOutput();
                    scenario.enter.execute(b, room, "", out);
                    var barrier = new CyclicBarrier(2);
                    var leaving = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.leave.execute(a, room, 0);
                        }
                    );
                    var disconnecting = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            scenario.sessions.close(new SessionId(a));
                            scenario.rooms.release(a, "PEER_CLOSED");
                            scenario.channels.release(a, "PEER_CLOSED");
                            return true;
                        }
                    );
                    leaving.get(5, TimeUnit.SECONDS);
                    disconnecting.get(5, TimeUnit.SECONDS);

                    assertEquals(b, scenario.rooms.current(b).orElseThrow().ownerSessionId());
                    assertEquals(1, out.updates.size());
                    assertEquals(List.of(List.of(0)), out.changedSlots);
                    assertEquals(List.of(true), out.ownerChanges);
                    assertEquals(1, count(logs, "room.owner.changed"));
                    assertEquals(1, count(logs, "room.left"));
                    assertEquals(1, scenario.channels.catalog().fills().get(1));
                    assertTrue(scenario.sessions.principal(new SessionId(a)).isEmpty());
                }
            }
        }
    }

    @Test
    void handlesTheOwnerAndNextSuccessorDisconnectingTogetherWithoutDroppingTheLastPlayer() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var a = scenario.player(1, 1);
                    var b = scenario.player(2, 1);
                    var c = scenario.player(3, 1);
                    int room = scenario.create.execute(
                        a,
                        RoomScenario.command(),
                        new RecordingRoomOutput()
                    ).room().id().value();
                    var out = new RecordingRoomOutput();
                    scenario.enter.execute(b, room, "", out);
                    scenario.enter.execute(c, room, "", new RecordingRoomOutput());
                    out.updates.clear();
                    out.changedSlots.clear();
                    out.ownerChanges.clear();
                    var barrier = new CyclicBarrier(2);
                    var tasks = List.of(a, c).stream().map(
                        id -> workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                scenario.sessions.close(new SessionId(id));
                                scenario.rooms.release(id, "PEER_CLOSED");
                                scenario.channels.release(id, "PEER_CLOSED");
                                return true;
                            }
                        )
                    ).toList();

                    for (var task : tasks) {
                        assertTrue(task.get(5, TimeUnit.SECONDS));
                    }

                    var snapshot = scenario.rooms.current(b).orElseThrow();
                    assertEquals(b, snapshot.ownerSessionId());
                    assertEquals(b, snapshot.slots().get(2).participant().sessionId());
                    assertEquals(2, out.updates.size());
                    assertEquals(List.of(0, 1), out.changedSlots.stream().flatMap(List::stream).sorted().toList());
                    assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
                    assertEquals(1, scenario.channels.catalog().fills().get(1));
                }
            }
        }
    }

    @Test
    void destroysTheLastRoomOnceAndRefusesNewAdmissionAfterRepeatedShutdown() throws Exception {
        try (
            var scenario = new RoomScenario();
            var workers = Executors.newFixedThreadPool(2);
            var logs = new LogCapture()
        ) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var context = scenario.channels.membership(a).orElseThrow();
            var participant = scenario.owner(a);
            var created = scenario.create.execute(a, RoomScenario.command(), new RecordingRoomOutput()).room();
            scenario.enter.execute(b, created.id().value(), "", new RecordingRoomOutput());
            var old = scenario.rooms.membership(a).orElseThrow();
            var barrier = new CyclicBarrier(2);
            var tasks = List.of(a, b).stream().map(
                id -> workers.submit(
                    () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        scenario.sessions.close(new SessionId(id));
                        scenario.rooms.release(id, "PEER_CLOSED");
                        scenario.channels.release(id, "PEER_CLOSED");
                        return true;
                    }
                )
            ).toList();

            for (var task : tasks) {
                assertTrue(task.get(5, TimeUnit.SECONDS));
            }

            scenario.rooms.release(old, "LATE");
            scenario.rooms.release(a, "REPEATED");
            scenario.rooms.close();
            scenario.rooms.close();

            assertTrue(scenario.rooms.forChannel(1).isEmpty());
            assertTrue(scenario.channels.membership(a).isEmpty());
            assertTrue(scenario.channels.membership(b).isEmpty());
            assertEquals(0, scenario.channels.catalog().fills().get(1));
            assertEquals(1, count(logs, "room.destroyed"));
            assertEquals(
                CreateRoomOutcome.IGNORED,
                scenario.rooms.create(
                    context,
                    RoomScenario.settings(),
                    null,
                    participant,
                    new RecordingRoomOutput()
                ).outcome()
            );
            assertEquals(
                EnterRoomOutcome.IGNORED,
                scenario.rooms.enter(
                    context,
                    new RoomEntryTarget(created.id(), created.generation(), 1),
                    participant,
                    "",
                    new RecordingRoomOutput()
                ).outcome()
            );
        }
    }

    private long count(LogCapture logs, String name) {
        return logs.events().stream().filter(
            event -> event.getKeyValuePairs().stream().anyMatch(
                field -> field.key.equals("event") && field.value.equals(name)
            )
        ).count();
    }
}

package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.service.model.EnterRoomOutcome;

class RoomEntryConcurrencyTest {
    @Test
    void admitsExactlyOneContenderForTheLastSlotAndNeverPublishesAnUpdateBeforeInit() throws Exception {
        try (var scenario = new RoomScenario(); var workers = Executors.newFixedThreadPool(2)) {
            var owner = scenario.player(1, 1);
            var output = new RecordingRoomOutput();
            int room = scenario.create.execute(owner, RoomScenario.command(), output).room().id().value();

            for (int i = 2; i <= 7; i++) {
                var id = scenario.player(i, 1);
                assertEquals(
                    EnterRoomOutcome.ENTERED,
                    scenario.enter.execute(id, room, new RecordingRoomOutput()).outcome()
                );
            }

            var a = scenario.player(8, 1);
            var b = scenario.player(9, 1);
            var contextA = scenario.channels.membership(a).orElseThrow();
            var contextB = scenario.channels.membership(b).orElseThrow();
            var target = scenario.rooms.entryTarget(contextA, room).orElseThrow();
            var barrier = new CyclicBarrier(2);
            var outA = new RecordingRoomOutput();
            var outB = new RecordingRoomOutput();
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return scenario.rooms.enter(contextA, target, scenario.owner(a), outA);
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return scenario.rooms.enter(contextB, target, scenario.owner(b), outB);
                }
            );
            var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(value -> value.outcome() == EnterRoomOutcome.ENTERED).count());
            assertEquals(1, results.stream().filter(value -> value.outcome() == EnterRoomOutcome.FULL).count());
            assertEquals(1, outA.initializations.size() + outB.initializations.size());
            assertEquals(8, scenario.rooms.forChannel(1).getFirst().playerCount());
            var slots = scenario.rooms.current(owner).orElseThrow().slots();
            assertEquals(8, slots.stream().map(slot -> slot.participant().sessionId()).distinct().count());
        }
    }

    @Test
    void queuesCreationInitBeforeAConcurrentJoinAndSerializesJoinWithOwnerDeparture() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 15; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var a = scenario.player(1, 1);
                    var b = scenario.player(2, 1);
                    var c = scenario.player(3, 1);
                    var out = new RecordingRoomOutput();
                    out.onUpdate = () -> assertEquals(1, out.initializations.size());
                    var contextA = scenario.channels.membership(a).orElseThrow();
                    var contextB = scenario.channels.membership(b).orElseThrow();
                    var barrier = new CyclicBarrier(2);
                    var creation = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.create(contextA, RoomScenario.settings(), scenario.owner(a), out);
                        }
                    );
                    var joining = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            var target = scenario.rooms.entryTarget(contextB, 1);
                            return target.map(
                                value -> scenario.rooms.enter(
                                    contextB,
                                    value,
                                    scenario.owner(b),
                                    new RecordingRoomOutput()
                                )
                            );
                        }
                    );
                    creation.get(5, TimeUnit.SECONDS);
                    joining.get(5, TimeUnit.SECONDS);
                    var contextC = scenario.channels.membership(c).orElseThrow();
                    var target = scenario.rooms.entryTarget(contextC, 1).orElseThrow();
                    var nextBarrier = new CyclicBarrier(2);
                    var departure = workers.submit(
                        () -> {
                            nextBarrier.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(a, "PEER_CLOSED");
                            return true;
                        }
                    );
                    var entering = workers.submit(
                        () -> {
                            nextBarrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.enter(contextC, target, scenario.owner(c), new RecordingRoomOutput());
                        }
                    );
                    departure.get(5, TimeUnit.SECONDS);
                    var result = entering.get(5, TimeUnit.SECONDS);

                    if (result.outcome() == EnterRoomOutcome.ENTERED) {
                        assertTrue(scenario.rooms.current(c).isPresent());
                        assertNotEquals(a, scenario.rooms.current(c).orElseThrow().ownerSessionId());
                    } else {
                        assertEquals(EnterRoomOutcome.NOT_FOUND, result.outcome());
                        assertTrue(scenario.rooms.current(c).isEmpty());
                    }
                }
            }
        }
    }
}

package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.service.model.EnterRoomOutcome;

class QuickJoinConcurrencyTest {
    @Test
    void serializesQuickJoinAgainstOrdinaryEntryAndAnotherQuickJoinForTheLastSlot() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (boolean ordinary : new boolean[]{true, false}) {
                for (int iteration = 0; iteration < 10; iteration++) {
                    try (var scenario = new RoomScenario()) {
                        var owner = scenario.player(1, 1);
                        int room = scenario.create.execute(
                            owner,
                            RoomScenario.command(),
                            new RecordingRoomOutput()
                        ).room().id().value();

                        for (int i = 2; i <= 7; i++) {
                            var guest = scenario.player(i, 1);
                            scenario.enter.execute(guest, room, "", new RecordingRoomOutput());
                        }

                        var a = scenario.player(8, 1);
                        var b = scenario.player(9, 1);
                        var contextA = scenario.channels.membership(a).orElseThrow();
                        var contextB = scenario.channels.membership(b).orElseThrow();
                        var participantA = scenario.owner(a);
                        var participantB = scenario.owner(b);
                        var target = scenario.rooms.entryTarget(contextB, room).orElseThrow();
                        var outA = new RecordingRoomOutput();
                        var outB = new RecordingRoomOutput();
                        var barrier = new CyclicBarrier(2);
                        var first = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.quickJoin(contextA, participantA, outA);
                            }
                        );
                        var second = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return ordinary
                                    ? scenario.rooms.enter(contextB, target, participantB, "", outB)
                                    : scenario.rooms.quickJoin(contextB, participantB, outB);
                            }
                        );
                        var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

                        assertEquals(
                            1,
                            results.stream().filter(result -> result.outcome() == EnterRoomOutcome.ENTERED)
                                .count()
                        );
                        assertEquals(1, outA.initializations.size() + outB.initializations.size());
                        var rejected = List.of(EnterRoomOutcome.FULL, EnterRoomOutcome.NOT_FOUND);

                        assertEquals(1, results.stream().filter(result -> rejected.contains(result.outcome())).count());
                        assertEquals(8, scenario.rooms.forChannel(1).getFirst().playerCount());
                        assertEquals(
                            8,
                            scenario.rooms.current(owner).orElseThrow().slots().stream()
                                .map(slot -> slot.participant().sessionId()).distinct().count()
                        );
                    }
                }
            }
        }
    }

    @Test
    void keepsInitBeforeUpdatesAndDoesNotDestroyAnAdmissionRacingWithTheLastDeparture() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    var context = scenario.channels.membership(guest).orElseThrow();
                    var participant = scenario.owner(guest);
                    var output = new RecordingRoomOutput();
                    output.onUpdate = () -> assertEquals(1, output.initializations.size());
                    var barrier = new CyclicBarrier(2);
                    var joining = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.quickJoin(context, participant, output);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(owner, "LEFT");
                            return true;
                        }
                    );
                    var result = joining.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));

                    if (result.outcome() == EnterRoomOutcome.ENTERED) {
                        assertEquals(guest, scenario.rooms.current(guest).orElseThrow().ownerSessionId());
                        assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
                        assertEquals(List.of(true), output.ownerChanges);
                    } else {
                        assertEquals(EnterRoomOutcome.NOT_FOUND, result.outcome());
                        assertTrue(output.initializations.isEmpty());
                        assertTrue(scenario.rooms.forChannel(1).isEmpty());
                    }
                }
            }
        }
    }
}

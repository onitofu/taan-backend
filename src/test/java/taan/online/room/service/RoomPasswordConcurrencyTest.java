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

class RoomPasswordConcurrencyTest {
    @Test
    void checksPasswordsAndClaimsTheLastSlotInOneTransition() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (boolean bothCorrect : new boolean[]{true, false}) {
                for (int iteration = 0; iteration < 10; iteration++) {
                    try (var scenario = new RoomScenario()) {
                        var owner = scenario.player(1, 1);
                        int room = scenario.create.execute(
                            owner,
                            RoomScenario.command(5, 0, "Room", 0, 0, 2, 0, 8, "secret", 0),
                            new RecordingRoomOutput()
                        ).room().id().value();

                        for (int i = 2; i <= 7; i++) {
                            var guest = scenario.player(i, 1);
                            scenario.enter.execute(guest, room, "secret", new RecordingRoomOutput());
                        }

                        var a = scenario.player(8, 1);
                        var b = scenario.player(9, 1);
                        var contextA = scenario.channels.membership(a).orElseThrow();
                        var contextB = scenario.channels.membership(b).orElseThrow();
                        var target = scenario.rooms.entryTarget(contextA, room).orElseThrow();
                        var outA = new RecordingRoomOutput();
                        var outB = new RecordingRoomOutput();
                        var barrier = new CyclicBarrier(2);
                        var first = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.enter(contextA, target, scenario.owner(a), "secret", outA);
                            }
                        );
                        var second = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.enter(
                                    contextB,
                                    target,
                                    scenario.owner(b),
                                    bothCorrect ? "secret" : "wrong",
                                    outB
                                );
                            }
                        );
                        var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

                        assertEquals(
                            1,
                            results.stream().filter(result -> result.outcome() == EnterRoomOutcome.ENTERED)
                                .count()
                        );
                        assertEquals(1, outA.initializations.size() + outB.initializations.size());
                        assertEquals(8, scenario.rooms.forChannel(1).getFirst().playerCount());
                        assertEquals(
                            8,
                            scenario.rooms.current(owner).orElseThrow().slots().stream()
                                .map(slot -> slot.participant().sessionId()).distinct().count()
                        );

                        if (bothCorrect) {
                            assertEquals(
                                1,
                                results.stream().filter(result -> result.outcome() == EnterRoomOutcome.FULL)
                                    .count()
                            );
                        } else {
                            assertTrue(scenario.rooms.membership(a).isPresent());
                            assertTrue(scenario.rooms.membership(b).isEmpty());
                            assertTrue(outB.initializations.isEmpty());
                            assertTrue(
                                List.of(EnterRoomOutcome.WRONG_PASSWORD, EnterRoomOutcome.FULL)
                                    .contains(results.get(1).outcome())
                            );
                        }
                    }
                }
            }
        }
    }
}

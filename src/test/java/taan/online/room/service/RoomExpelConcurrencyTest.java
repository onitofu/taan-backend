package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ExpelRoomParticipantOutcome;

class RoomExpelConcurrencyTest {
    @Test
    void serializesExpulsionWithOwnerDepartureTargetLeaveAndTeamChanges() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int scenarioId = 0; scenarioId < 3; scenarioId++) {
                int operation = scenarioId;
                for (int iteration = 0; iteration < 20; iteration++) {
                    try (var scenario = new RoomScenario()) {
                        var owner = scenario.player(1, 1);
                        var guest = scenario.player(2, 1);
                        var observer = scenario.player(3, 1);
                        scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                        scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                        scenario.enter.execute(observer, 1, "", new RecordingRoomOutput());
                        var barrier = new CyclicBarrier(2);
                        var kicking = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.expel(owner, 2);
                            }
                        );
                        var changing = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                if (operation == 0) {
                                    scenario.rooms.release(owner, "DISCONNECTED");
                                } else if (operation == 1) {
                                    scenario.rooms.leave(guest, 1, 0);
                                } else {
                                    scenario.rooms.changeTeam(guest, RoomTeam.RED);
                                }
                                return true;
                            }
                        );
                        var outcome = kicking.get(5, TimeUnit.SECONDS).outcome();
                        assertTrue(changing.get(5, TimeUnit.SECONDS));
                        var snapshot = scenario.rooms.current(observer).orElseThrow();

                        if (operation == 0) {
                            assertEquals(observer, snapshot.ownerSessionId());
                            assertEquals(
                                outcome != ExpelRoomParticipantOutcome.EXPELLED,
                                scenario.rooms.membership(guest).isPresent()
                            );
                        } else {
                            assertTrue(scenario.rooms.membership(guest).isEmpty());
                            assertEquals(owner, snapshot.ownerSessionId());
                        }

                        assertEquals(
                            snapshot.slots().stream().filter(slot -> slot.state().occupied()).count(),
                            scenario.rooms.forChannel(1).getFirst().playerCount()
                        );
                        assertEquals(
                            snapshot.slots().stream().filter(slot -> slot.participant() != null).count(),
                            snapshot.slots().stream().filter(slot -> slot.participant() != null)
                                .map(slot -> slot.participant().sessionId()).distinct().count()
                        );
                    }
                }
            }
        }
    }
}

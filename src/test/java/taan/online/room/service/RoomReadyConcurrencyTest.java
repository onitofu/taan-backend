package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.OCCUPIED;
import static taan.online.room.model.RoomSlotState.READY;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomTeamOutcome;

class RoomReadyConcurrencyTest {
    @Test
    void serializesReadinessAgainstTeamChangesAndOwnerDeparture() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    var a = new RecordingRoomOutput();
                    var b = new RecordingRoomOutput();
                    scenario.create.execute(owner, RoomScenario.command(), a);
                    scenario.enter.execute(guest, 1, "", b);
                    var barrier = new CyclicBarrier(2);
                    var setting = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, true);
                        }
                    );
                    var moving = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(guest, RoomTeam.BLUE);
                        }
                    );
                    setting.get(5, TimeUnit.SECONDS);
                    var moved = moving.get(5, TimeUnit.SECONDS);
                    int index = moved.outcome() == ChangeRoomTeamOutcome.CHANGED ? 1 : 2;

                    assertEquals(READY, scenario.rooms.current(guest).orElseThrow().slots().get(index).state());
                    assertEquals(
                        guest,
                        scenario.rooms.current(guest).orElseThrow().slots().get(index).participant().sessionId()
                    );
                    scenario.rooms.setReady(guest, false);
                    var next = new CyclicBarrier(2);
                    var readying = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, true);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(owner, "DISCONNECTED");
                            return true;
                        }
                    );
                    readying.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));
                    var remaining = scenario.rooms.current(guest).orElseThrow();

                    assertEquals(guest, remaining.ownerSessionId());
                    assertEquals(OCCUPIED, remaining.slots().get(index).state());
                    assertEquals(false, b.readyValues.getLast());
                }
            }
        }
    }

    @Test
    void ordersNewSnapshotsBeforeNotificationsAndNeverResurrectsDepartedGuests() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    var next = scenario.player(3, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                    var c = new RecordingRoomOutput();
                    c.onReady = () -> assertEquals(1, c.initializations.size());
                    var context = scenario.channels.membership(next).orElseThrow();
                    var target = scenario.rooms.entryTarget(context, 1).orElseThrow();
                    var actor = scenario.owner(next);
                    var barrier = new CyclicBarrier(2);
                    var setting = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, true);
                        }
                    );
                    var joining = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.enter(context, target, actor, "", c);
                        }
                    );
                    setting.get(5, TimeUnit.SECONDS);
                    joining.get(5, TimeUnit.SECONDS);

                    assertEquals(READY, scenario.rooms.current(next).orElseThrow().slots().get(2).state());
                    assertTrue(
                        c.initializations.getFirst().slots().get(2).state() == READY || !c.readyValues.isEmpty()
                    );
                    var second = new CyclicBarrier(2);
                    var cancelling = workers.submit(
                        () -> {
                            second.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, false);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            second.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(guest, "DISCONNECTED");
                            return true;
                        }
                    );
                    cancelling.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));
                    var remaining = scenario.rooms.current(owner).orElseThrow();

                    assertTrue(scenario.rooms.membership(guest).isEmpty());
                    assertEquals(2, remaining.slots().stream().filter(slot -> slot.state().occupied()).count());
                    assertTrue(remaining.slots().stream().noneMatch(slot -> slot.participant() != null
                        && slot.participant().sessionId().equals(guest)));
                }
            }
        }
    }
}

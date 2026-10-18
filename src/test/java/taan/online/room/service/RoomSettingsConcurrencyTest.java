package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;

class RoomSettingsConcurrencyTest {
    @Test
    void serializesSettingsWithReadyAndOwnerDepartureWithoutResettingGuests() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                    var first = new CyclicBarrier(2);
                    var changing = workers.submit(
                        () -> {
                            first.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeSettings(owner, 2, 1, 0);
                        }
                    );
                    var readying = workers.submit(
                        () -> {
                            first.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, true);
                        }
                    );
                    changing.get(5, TimeUnit.SECONDS);
                    readying.get(5, TimeUnit.SECONDS);

                    assertEquals(
                        RoomSlotState.READY,
                        scenario.rooms.current(guest).orElseThrow().slots().get(2).state()
                    );
                    var next = new CyclicBarrier(2);
                    var setting = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeMap(owner, 4);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(owner, "DISCONNECTED");
                            return true;
                        }
                    );
                    var result = setting.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));
                    var remaining = scenario.rooms.current(guest).orElseThrow();

                    assertEquals(guest, remaining.ownerSessionId());
                    assertEquals(
                        result.outcome() == ChangeRoomSettingsOutcome.CHANGED ? 4 : 2,
                        remaining.settings().mapId()
                    );
                    assertEquals(RoomSlotState.OCCUPIED, remaining.slots().get(2).state());
                }
            }
        }
    }

    @Test
    void deliversInitBeforeLaterSettingsWithoutMutatingTheInitialSnapshot() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    var output = new RecordingRoomOutput();
                    output.onSettingsChanged = () -> assertEquals(1, output.initializations.size());
                    var context = scenario.channels.membership(guest).orElseThrow();
                    var target = scenario.rooms.entryTarget(context, 1).orElseThrow();
                    var participant = scenario.owner(guest);
                    var barrier = new CyclicBarrier(2);
                    var changing = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeSettings(owner, 2, 1, 0);
                        }
                    );
                    var joining = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.enter(context, target, participant, "", output);
                        }
                    );
                    changing.get(5, TimeUnit.SECONDS);
                    joining.get(5, TimeUnit.SECONDS);
                    var initial = output.initializations.getFirst();

                    assertTrue(initial.settings().mapId() == 2 || output.settings.getLast().mapId() == 2);
                    assertEquals(2, scenario.rooms.current(guest).orElseThrow().settings().mapId());
                }
            }
        }
    }
}

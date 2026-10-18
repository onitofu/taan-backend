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
import taan.online.room.model.RoomTeam;

class RoomWeaponConcurrencyTest {
    @Test
    void ordersSelectionAgainstReadyTeamChangesAndDeparture() throws Exception {
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
                    var selection = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeWeapon(guest, 2, 0);
                        }
                    );
                    var ready = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setReady(guest, true);
                        }
                    );
                    var selected = selection.get(5, TimeUnit.SECONDS);
                    ready.get(5, TimeUnit.SECONDS);
                    int weapon = 0;

                    assertEquals(
                        weapon,
                        scenario.rooms.current(guest).orElseThrow().slots().get(2).participant().weaponId()
                    );
                    scenario.rooms.setReady(guest, false);
                    var next = new CyclicBarrier(2);
                    var choosing = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeWeapon(guest, 2, 0);
                        }
                    );
                    var moving = workers.submit(
                        () -> {
                            next.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(guest, RoomTeam.BLUE);
                        }
                    );
                    choosing.get(5, TimeUnit.SECONDS);
                    moving.get(5, TimeUnit.SECONDS);

                    assertEquals(
                        0,
                        scenario.rooms.current(guest).orElseThrow().slots().get(1).participant().weaponId()
                    );
                    var last = new CyclicBarrier(2);
                    var changing = workers.submit(
                        () -> {
                            last.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeWeapon(guest, 2, 0);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            last.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(guest, "DISCONNECTED");
                            return true;
                        }
                    );
                    changing.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));

                    assertTrue(scenario.rooms.current(guest).isEmpty());
                    assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
                }
            }
        }
    }

    @Test
    void initializesNewMembersBeforePublishingLaterSelections() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    var output = new RecordingRoomOutput();
                    output.onWeaponChanged = () -> assertEquals(1, output.initializations.size());
                    var context = scenario.channels.membership(guest).orElseThrow();
                    var target = scenario.rooms.entryTarget(context, 1).orElseThrow();
                    var participant = scenario.owner(guest);
                    var barrier = new CyclicBarrier(2);
                    var selection = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeWeapon(owner, 1, 0);
                        }
                    );
                    var joining = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.enter(context, target, participant, "", output);
                        }
                    );
                    selection.get(5, TimeUnit.SECONDS);
                    joining.get(5, TimeUnit.SECONDS);
                    int initial = output.initializations.getFirst().slots().get(0).participant().weaponId();

                    assertTrue(initial == 0 || output.weaponIds.equals(List.of(0)));
                    assertEquals(
                        0,
                        scenario.rooms.current(guest).orElseThrow().slots().get(0).participant().weaponId()
                    );
                }
            }
        }
    }
}

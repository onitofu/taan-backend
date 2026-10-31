package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.CLOSED;
import static taan.online.room.model.RoomSlotState.OPEN;
import static taan.online.room.model.RoomTeam.BLUE;
import static taan.online.room.model.RoomTeam.RED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.CHANGED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.NO_OPEN_SLOT;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.REJECTED;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.SlotAvailabilityOutcome;

class RoomTeamConcurrencyTest {
    @Test
    void givesTheLastTargetPlaceToExactlyOneOfTwoTeamChanges() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var red = scenario.player(2, 1);
                    var blue = scenario.player(3, 1);
                    var output = new RecordingRoomOutput();
                    scenario.create.execute(owner, RoomScenario.command(), output);
                    scenario.enter.execute(red, 1, "", new RecordingRoomOutput());
                    scenario.enter.execute(blue, 1, "", new RecordingRoomOutput());
                    scenario.rooms.setSlotAvailability(owner, 6, CLOSED);
                    scenario.rooms.setSlotAvailability(owner, 7, CLOSED);
                    var barrier = new CyclicBarrier(2);
                    var first = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(owner, RED);
                        }
                    );
                    var second = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(blue, RED);
                        }
                    );
                    var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                    var snapshot = scenario.rooms.current(owner).orElseThrow();

                    assertEquals(1, results.stream().filter(value -> value.outcome() == CHANGED).count());
                    assertEquals(1, results.stream().filter(value -> value.outcome() == NO_OPEN_SLOT).count());
                    assertEquals(1, output.teamChanges.size());
                    assertEquals(3, snapshot.slots().stream().filter(slot -> slot.participant() != null).count());
                    assertEquals(
                        3,
                        snapshot.slots().stream().filter(slot -> slot.participant() != null)
                        .map(slot -> slot.participant().sessionId()).distinct().count()
                    );
                    assertEquals(owner, snapshot.ownerSessionId());
                    assertEquals(red, snapshot.slots().get(2).participant().sessionId());
                }
            }
        }
    }

    @Test
    void serializesTransfersAgainstOrdinaryAndQuickAdmissionWithoutOverwritingEitherPlayer() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (boolean quick : new boolean[]{true, false}) {
                for (int iteration = 0; iteration < 15; iteration++) {
                    try (var scenario = new RoomScenario()) {
                        var owner = scenario.player(1, 1);
                        var guest = scenario.player(2, 1);
                        var output = new RecordingRoomOutput();
                        scenario.create.execute(owner, RoomScenario.command(), output);

                        for (int index : new int[]{1, 3, 4, 5, 6, 7}) {
                            scenario.rooms.setSlotAvailability(owner, index, CLOSED);
                        }

                        var context = scenario.channels.membership(guest).orElseThrow();
                        var participant = scenario.owner(guest);
                        var target = scenario.rooms.entryTarget(context, 1).orElseThrow();
                        var joined = new RecordingRoomOutput();
                        var barrier = new CyclicBarrier(2);
                        var changing = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.changeTeam(owner, RED);
                            }
                        );
                        var entering = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return quick ? scenario.rooms.quickJoin(context, participant, joined)
                                    : scenario.rooms.enter(context, target, participant, "", joined);
                            }
                        );
                        var changed = changing.get(5, TimeUnit.SECONDS);
                        var entered = entering.get(5, TimeUnit.SECONDS);
                        var snapshot = scenario.rooms.current(owner).orElseThrow();

                        assertEquals(EnterRoomOutcome.ENTERED, entered.outcome());
                        assertEquals(2, snapshot.slots().stream().filter(slot -> slot.participant() != null).count());
                        assertEquals(
                            2,
                            snapshot.slots().stream().filter(slot -> slot.participant() != null)
                            .map(slot -> slot.participant().sessionId()).distinct().count()
                        );
                        assertEquals(
                            changed.outcome() == CHANGED ? owner : guest,
                            snapshot.slots().get(2).participant().sessionId()
                        );
                        assertEquals(
                            changed.outcome() == CHANGED ? guest : owner,
                            snapshot.slots().get(0).participant().sessionId()
                        );
                    }
                }
            }
        }
    }

    @Test
    void ordersClosingAPlaceAgainstATransferAndDepartureAgainstATransfer() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    var output = new RecordingRoomOutput();
                    scenario.create.execute(owner, RoomScenario.command(), output);

                    for (int index : new int[]{3, 6, 7}) {
                        scenario.rooms.setSlotAvailability(owner, index, CLOSED);
                    }

                    var barrier = new CyclicBarrier(2);
                    var changing = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(owner, RED);
                        }
                    );
                    var closing = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setSlotAvailability(owner, 2, CLOSED);
                        }
                    );
                    var changed = changing.get(5, TimeUnit.SECONDS);
                    var closed = closing.get(5, TimeUnit.SECONDS);

                    assertEquals(
                        changed.outcome() == CHANGED ? SlotAvailabilityOutcome.REJECTED
                        : SlotAvailabilityOutcome.CHANGED,
                        closed.outcome()
                    );
                    assertEquals(changed.outcome() == CHANGED ? 1 : 0, output.teamChanges.size());
                    scenario.rooms.setSlotAvailability(owner, 3, OPEN);
                    scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                    scenario.rooms.changeTeam(owner, BLUE);
                    scenario.rooms.setSlotAvailability(owner, 6, OPEN);
                    var secondBarrier = new CyclicBarrier(2);
                    var transfer = workers.submit(
                        () -> {
                            secondBarrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.changeTeam(owner, RED);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            secondBarrier.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(owner, "DISCONNECTED");
                            return true;
                        }
                    );
                    transfer.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));
                    var remaining = scenario.rooms.current(guest).orElseThrow();

                    assertEquals(guest, remaining.ownerSessionId());
                    assertEquals(1, remaining.slots().stream().filter(slot -> slot.participant() != null).count());
                    assertEquals(REJECTED, scenario.rooms.changeTeam(owner, RED).outcome());
                }
            }
        }
    }
}

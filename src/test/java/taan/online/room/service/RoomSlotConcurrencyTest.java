package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.CLOSED;
import static taan.online.room.model.RoomSlotState.OPEN;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.SlotAvailabilityOutcome;

class RoomSlotConcurrencyTest {
    @Test
    void serializesClosingTheLastPlaceAgainstOrdinaryAndQuickEntry() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (boolean quick : new boolean[]{true, false}) {
                for (int iteration = 0; iteration < 20; iteration++) {
                    try (var scenario = new RoomScenario()) {
                        var owner = scenario.player(1, 1);
                        var guest = scenario.player(2, 1);
                        var ownerOutput = new RecordingRoomOutput();
                        scenario.create.execute(owner, RoomScenario.command(), ownerOutput);

                        for (int slot = 1; slot < 7; slot++) {
                            scenario.rooms.setSlotAvailability(owner, slot, CLOSED);
                        }

                        ownerOutput.availabilitySlots.clear();
                        var context = scenario.channels.membership(guest).orElseThrow();
                        var participant = scenario.owner(guest);
                        var target = scenario.rooms.entryTarget(context, 1).orElseThrow();
                        var output = new RecordingRoomOutput();
                        var barrier = new CyclicBarrier(2);
                        var closing = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return scenario.rooms.setSlotAvailability(owner, 7, CLOSED);
                            }
                        );
                        var entering = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return quick ? scenario.rooms.quickJoin(context, participant, output)
                                    : scenario.rooms.enter(context, target, participant, "", output);
                            }
                        );
                        var closed = closing.get(5, TimeUnit.SECONDS);
                        var entered = entering.get(5, TimeUnit.SECONDS);
                        var slot = scenario.rooms.current(owner).orElseThrow().slots().get(7);

                        if (closed.outcome() == SlotAvailabilityOutcome.CHANGED) {
                            assertEquals(CLOSED, slot.state());
                            assertEquals(
                                quick ? EnterRoomOutcome.NOT_FOUND : EnterRoomOutcome.NO_SLOT,
                                entered.outcome()
                            );
                            assertTrue(output.initializations.isEmpty());
                            assertEquals(1, ownerOutput.availabilitySlots.size());
                        } else {
                            assertEquals(SlotAvailabilityOutcome.REJECTED, closed.outcome());
                            assertEquals(EnterRoomOutcome.ENTERED, entered.outcome());
                            assertEquals(guest, slot.participant().sessionId());
                            assertTrue(ownerOutput.availabilitySlots.isEmpty());
                        }
                    }
                }
            }
        }
    }

    @Test
    void ordersOwnerDepartureAgainstChangesAndLeavesNoRoomStateAfterDeletion() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var scenario = new RoomScenario()) {
                    var owner = scenario.player(1, 1);
                    var guest = scenario.player(2, 1);
                    scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                    scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                    var barrier = new CyclicBarrier(2);
                    var closing = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return scenario.rooms.setSlotAvailability(owner, 1, CLOSED);
                        }
                    );
                    var leaving = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            scenario.rooms.release(owner, "DISCONNECTED");
                            return true;
                        }
                    );
                    var result = closing.get(5, TimeUnit.SECONDS);
                    assertTrue(leaving.get(5, TimeUnit.SECONDS));
                    var current = scenario.rooms.current(guest).orElseThrow();

                    assertEquals(guest, current.ownerSessionId());
                    assertEquals(
                        result.outcome() == SlotAvailabilityOutcome.CHANGED ? CLOSED : OPEN,
                        current.slots().get(1).state()
                    );
                    assertEquals(
                        SlotAvailabilityOutcome.REJECTED,
                        scenario.rooms.setSlotAvailability(owner, 1, OPEN).outcome()
                    );
                    scenario.rooms.release(guest, "DISCONNECTED");
                    scenario.lobby(owner);
                    var recreated = scenario.create.execute(
                        owner,
                        RoomScenario.command(),
                        new RecordingRoomOutput()
                    ).room();

                    assertEquals(OPEN, recreated.slots().get(1).state());
                }
            }
        }
    }
}

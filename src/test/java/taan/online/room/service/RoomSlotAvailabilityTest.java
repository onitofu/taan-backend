package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.CLOSED;
import static taan.online.room.model.RoomSlotState.OPEN;
import static taan.online.room.service.model.SlotAvailabilityOutcome.CHANGED;
import static taan.online.room.service.model.SlotAvailabilityOutcome.IGNORED;
import static taan.online.room.service.model.SlotAvailabilityOutcome.REJECTED;
import static taan.online.room.service.model.SlotAvailabilityOutcome.UNCHANGED;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.EnterRoomOutcome;

class RoomSlotAvailabilityTest {
    @Test
    void broadcastsChangesConfirmsRepeatsOnlyToTheSenderAndPreservesListings() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var outsider = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var ownerOutput = new RecordingRoomOutput();
            var guestOutput = new RecordingRoomOutput();
            var outsiderOutput = new RecordingRoomOutput();
            var foreignOutput = new RecordingRoomOutput();
            var created = scenario.create.execute(owner, RoomScenario.command(), ownerOutput).room();
            scenario.enter.execute(guest, created.id().value(), "", guestOutput);
            scenario.create.execute(outsider, RoomScenario.command(), outsiderOutput);
            scenario.create.execute(foreign, RoomScenario.command(), foreignOutput);
            var listings = scenario.rooms.forChannel(1);
            var action = new SetRoomSlotAvailabilityAction(scenario.rooms);

            assertEquals(CHANGED, action.execute(owner, 1, CLOSED).outcome());
            assertEquals(UNCHANGED, action.execute(owner, 1, CLOSED).outcome());
            assertEquals(2, ownerOutput.availabilitySlots.size());
            assertEquals(1, guestOutput.availabilitySlots.size());
            assertEquals(CLOSED, guestOutput.availabilityStates.getFirst());
            assertTrue(outsiderOutput.availabilitySlots.isEmpty());
            assertTrue(foreignOutput.availabilitySlots.isEmpty());
            assertSame(listings, scenario.rooms.forChannel(1));
            assertEquals(
                2,
                scenario.rooms.current(owner).orElseThrow().slots().stream()
                    .filter(slot -> slot.participant() != null).count()
            );
            assertEquals(CHANGED, action.execute(owner, 1, OPEN).outcome());
            assertEquals(UNCHANGED, action.execute(owner, 1, OPEN).outcome());
            assertEquals(4, ownerOutput.availabilitySlots.size());
            assertEquals(2, guestOutput.availabilitySlots.size());
            assertTrue(logs.text().contains("room.slot.closed"));
            assertTrue(logs.text().contains("room.slot.opened"));
            assertTrue(logs.text().contains("slotIndex=\"1\""));
            assertTrue(logs.text().contains("outcome=\"UNCHANGED\""));
            assertFalse(logs.text().contains("Hero1"));
        }
    }

    @Test
    void rejectsNonOwnersOccupiedPlacesAndInvalidInputsWithoutNotifyingAnyone() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var output = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), output);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            var action = new SetRoomSlotAvailabilityAction(scenario.rooms);

            assertEquals("NOT_OWNER", action.execute(guest, 1, CLOSED).reason());
            assertEquals("NO_MEMBERSHIP", action.execute(UUID.randomUUID(), 1, CLOSED).reason());
            assertEquals("OCCUPIED_SLOT", action.execute(owner, 0, OPEN).reason());
            assertEquals("OCCUPIED_SLOT", action.execute(owner, 2, CLOSED).reason());

            for (int index : new int[]{-1, 8, 255}) {
                assertEquals(REJECTED, action.execute(owner, index, OPEN).outcome());
            }

            assertEquals(REJECTED, action.execute(owner, 1, null).outcome());
            assertEquals(REJECTED, action.execute(owner, 1, RoomSlotState.OCCUPIED).outcome());
            assertEquals(REJECTED, action.execute(owner, 1, RoomSlotState.READY).outcome());
            assertTrue(output.availabilitySlots.isEmpty());
            assertEquals(OPEN, scenario.rooms.current(owner).orElseThrow().slots().get(1).state());
            assertTrue(logs.text().contains("room.slot.availability.rejected"));
        }
    }

    @Test
    void closesAllFreePlacesWithoutChangingCapacityAndReopensAdmission() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var output = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), output);

            for (int slot = 1; slot < 8; slot++) {
                assertEquals(CHANGED, scenario.rooms.setSlotAvailability(owner, slot, CLOSED).outcome());
            }

            assertEquals(EnterRoomOutcome.NO_SLOT, scenario.enter.execute(guest, 1, "", output).outcome());
            assertEquals(EnterRoomOutcome.NOT_FOUND, scenario.quickJoin.execute(guest, 65535, output).outcome());
            assertEquals(8, scenario.rooms.forChannel(1).getFirst().capacity());
            assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
            scenario.rooms.setSlotAvailability(owner, 7, OPEN);
            var guestOutput = new RecordingRoomOutput();

            assertEquals(EnterRoomOutcome.ENTERED, scenario.quickJoin.execute(guest, 65535, guestOutput).outcome());
            var snapshot = guestOutput.initializations.getFirst();
            assertEquals(guest, snapshot.slots().get(7).participant().sessionId());
            assertTrue(snapshot.slots().subList(1, 7).stream().allMatch(slot -> slot.state() == CLOSED));
            scenario.rooms.release(owner, "DISCONNECTED");

            assertEquals(guest, scenario.rooms.current(guest).orElseThrow().ownerSessionId());
            assertEquals(CLOSED, scenario.rooms.current(guest).orElseThrow().slots().get(1).state());
            assertEquals(CHANGED, scenario.rooms.setSlotAvailability(guest, 1, OPEN).outcome());
            assertEquals(OPEN, scenario.rooms.current(guest).orElseThrow().slots().get(0).state());
            scenario.rooms.release(guest, "DISCONNECTED");

            assertTrue(scenario.rooms.forChannel(1).isEmpty());
        }
    }

    @Test
    void isolatesRecipientFailuresAndKeepsAcceptedState() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var ownerOutput = new RecordingRoomOutput();
            var guestOutput = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), ownerOutput);
            scenario.enter.execute(guest, 1, "", guestOutput);
            guestOutput.onSlotAvailability = () -> {
                throw new IllegalStateException("private slot failure");
            };
            var result = scenario.rooms.setSlotAvailability(owner, 1, CLOSED);
            result.delivery().toCompletableFuture().join();

            assertEquals(CHANGED, result.outcome());
            assertEquals(CLOSED, scenario.rooms.current(owner).orElseThrow().slots().get(1).state());
            assertEquals("ROOM_NOTIFICATION_FAILED", guestOutput.closed);
            assertNull(ownerOutput.closed);
            assertEquals(1, ownerOutput.availabilitySlots.size());
            assertTrue(logs.text().contains("room.notification.failed"));
            assertFalse(logs.text().contains("private slot failure"));
            ownerOutput.onSlotAvailability = guestOutput.onSlotAvailability;
            var failed = scenario.rooms.setSlotAvailability(owner, 1, OPEN);

            assertThrows(RuntimeException.class, () -> failed.delivery().toCompletableFuture().join());
            assertEquals(OPEN, scenario.rooms.current(owner).orElseThrow().slots().get(1).state());
        }
    }

    @Test
    void rejectsChangedChannelPhaseAndCharacterBindings() throws Exception {
        for (int change = 0; change < 3; change++) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var other = scenario.player(2, 1);
                var output = new RecordingRoomOutput();
                var created = scenario.create.execute(owner, RoomScenario.command(), output).room();

                if (change == 0) {
                    scenario.channels.move(owner, new MoveToCommand(0, 2, 0, 929));
                } else if (change == 1) {
                    scenario.lobby(owner);
                } else {
                    var field = RoomRegistry.class.getDeclaredField("rooms");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var rooms = (Map<RoomId, Room>) field.get(scenario.rooms);
                    var forged = new RoomParticipant(owner, scenario.owner(other).character(), 3);
                    rooms.put(
                        created.id(),
                        new Room(created.id(), created.generation(), 1, created.settings(), null, forged)
                    );
                }

                assertEquals("CONTEXT_CHANGED", scenario.rooms.setSlotAvailability(owner, 1, CLOSED).reason());
                assertTrue(output.availabilitySlots.isEmpty());
                assertEquals(OPEN, scenario.rooms.current(owner).orElseThrow().slots().get(1).state());
            }
        }
    }

    @Test
    void ignoresShutdownAndDisconnectionAndRejectsANonWaitingRoom() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            var field = RoomRegistry.class.getDeclaredField("rooms");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var rooms = (Map<RoomId, Room>) field.get(scenario.rooms);
            rooms.get(new RoomId(1)).close();

            assertEquals("ROOM_UNAVAILABLE", scenario.rooms.setSlotAvailability(owner, 1, CLOSED).reason());
            scenario.channels.release(owner, "SESSION_CLOSED");

            assertEquals(IGNORED, scenario.rooms.setSlotAvailability(owner, 1, CLOSED).outcome());
            scenario.rooms.close();

            assertEquals(IGNORED, scenario.rooms.setSlotAvailability(owner, 1, CLOSED).outcome());
        }
    }
}

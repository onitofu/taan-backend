package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.ExpelRoomParticipantOutcome.EXPELLED;
import static taan.online.room.service.model.ExpelRoomParticipantOutcome.IGNORED;
import static taan.online.room.service.model.ExpelRoomParticipantOutcome.REJECTED;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.LeaveRoomOutcome;

class ExpelRoomParticipantActionTest {
    @Test
    void freesReadyTargetsImmediatelyAndWaitsForTheirLeaveBeforeReadmission() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var observer = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            var d = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.enter.execute(observer, 1, "", c);
            scenario.create.execute(foreign, RoomScenario.command(), d);
            scenario.rooms.setReady(guest, true);
            scenario.rooms.setReady(observer, true);
            var original = scenario.rooms.current(owner).orElseThrow();
            var token = scenario.rooms.membership(guest).orElseThrow();
            var action = new ExpelRoomParticipantAction(scenario.rooms);
            a.onUpdate = () -> {
                assertEquals(List.of(new AccountId(2)), b.expelledAccounts);
                assertEquals(2, scenario.rooms.forChannel(1).getFirst().playerCount());
            };
            int updates = b.updates.size();

            assertEquals(EXPELLED, action.execute(owner, 2).outcome());
            assertTrue(scenario.rooms.current(guest).isEmpty());
            assertEquals(updates, b.updates.size());
            assertEquals(0, d.updates.size());
            var remaining = scenario.rooms.current(owner).orElseThrow();
            assertEquals(original.settings(), remaining.settings());
            assertEquals(owner, remaining.ownerSessionId());
            assertEquals(RoomSlotState.OPEN, remaining.slots().get(2).state());
            assertEquals(RoomSlotState.READY, remaining.slots().get(1).state());
            assertEquals("TARGET_NOT_FOUND", action.execute(owner, 2).reason());
            scenario.lobby(guest);

            assertEquals(
                CreateRoomOutcome.REJECTED,
                scenario.create.execute(guest, RoomScenario.command(), b).outcome()
            );
            assertEquals(EnterRoomOutcome.ALREADY_IN_ROOM, scenario.enter.execute(guest, 1, "", b).outcome());
            assertEquals(EnterRoomOutcome.ALREADY_IN_ROOM, scenario.quickJoin.execute(guest, 65535, b).outcome());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(guest, 1, 1).outcome());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(guest, 999, 0).outcome());
            assertEquals(LeaveRoomOutcome.LEFT, scenario.leave.execute(guest, 1, 0).outcome());
            assertEquals(LeaveRoomOutcome.REPLAYED, scenario.leave.execute(guest, 1, 0).outcome());
            a.onUpdate = () -> { };

            assertEquals(EnterRoomOutcome.ENTERED, scenario.enter.execute(guest, 1, "", b).outcome());
            var next = scenario.rooms.membership(guest).orElseThrow();
            scenario.rooms.release(token, "LATE_OLD_CLEANUP");

            assertEquals(next, scenario.rooms.membership(guest).orElseThrow());
            assertEquals(3, scenario.rooms.current(owner).orElseThrow().slots().get(2).participant().weaponId());
            assertTrue(logs.text().contains("room.expel.succeeded"));
            assertTrue(logs.text().contains("targetAccountId=\"2\""));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void rejectsGuestsSelfOtherRoomsOtherChannelsAndMissingTargetsSilently() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var other = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.create.execute(other, RoomScenario.command(), new RecordingRoomOutput());
            scenario.create.execute(foreign, RoomScenario.command(), new RecordingRoomOutput());
            var action = new ExpelRoomParticipantAction(scenario.rooms);

            assertEquals("NOT_OWNER", action.execute(guest, 1).reason());
            assertEquals("OWN_ACCOUNT", action.execute(owner, 1).reason());

            for (long target : new long[]{0, 3, 4, 999, 0xffff_ffffL}) {
                assertEquals(REJECTED, action.execute(owner, target).outcome());
            }

            scenario.channels.move(guest, new MoveToCommand(0, 2, 0, 929));
            assertEquals("CONTEXT_CHANGED", action.execute(owner, 2).reason());
            scenario.channels.release(guest, "DISCONNECT");
            assertEquals(IGNORED, action.execute(owner, 2).outcome());
            rooms(scenario.rooms).get(new RoomId(1)).close();
            assertEquals("ROOM_UNAVAILABLE", action.execute(owner, 2).reason());
            assertTrue(a.expelledAccounts.isEmpty());
            assertTrue(b.expelledAccounts.isEmpty());
            assertEquals(2, scenario.rooms.current(owner).orElseThrow().slots().stream()
                .filter(slot -> slot.state().occupied()).count());
        }
    }

    @Test
    void ignoresLostActorContextsAndDropsReceiptsOnDisconnect() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var action = new ExpelRoomParticipantAction(scenario.rooms);

            assertEquals(IGNORED, action.execute(UUID.randomUUID(), 2).outcome());
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            action.execute(owner, 2);
            scenario.rooms.release(guest, "DISCONNECT");

            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(guest, 1, 0).outcome());
            scenario.channels.release(owner, "DISCONNECT");
            assertEquals(IGNORED, action.execute(owner, 2).outcome());
            scenario.rooms.close();
            assertEquals(IGNORED, action.execute(owner, 2).outcome());
        }
    }

    @Test
    void keepsExpulsionIndependentOfSynchronousAndAsynchronousTargetDeliveryFailures() {
        for (boolean synchronous : new boolean[]{true, false}) {
            try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
                var owner = scenario.player(1, 1);
                var guest = scenario.player(2, 1);
                var target = new RecordingRoomOutput();
                scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                scenario.enter.execute(guest, 1, "", target);
                var future = new CompletableFuture<Void>();
                target.expelDelivery = future;

                if (synchronous) {
                    target.onExpelled = () -> {
                        throw new IllegalStateException("hidden@example.test");
                    };
                }

                var action = new ExpelRoomParticipantAction(scenario.rooms);
                assertEquals(EXPELLED, action.execute(owner, 2).outcome());
                assertTrue(scenario.rooms.current(guest).isEmpty());
                assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
                future.completeExceptionally(new IllegalStateException("hidden@example.test"));

                assertEquals("ROOM_NOTIFICATION_FAILED", target.closed);
                assertFalse(logs.text().contains("hidden@example.test"));
            }
        }
    }

    @Test
    void onlyTheCurrentOwnerCanExpelAfterHandoff() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var successor = scenario.player(3, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.enter.execute(successor, 1, "", new RecordingRoomOutput());
            scenario.rooms.release(owner, "DISCONNECT");
            var action = new ExpelRoomParticipantAction(scenario.rooms);

            assertEquals(successor, scenario.rooms.current(guest).orElseThrow().ownerSessionId());
            assertEquals(IGNORED, action.execute(owner, 2).outcome());
            assertEquals("NOT_OWNER", action.execute(guest, 3).reason());
            assertEquals(EXPELLED, action.execute(successor, 2).outcome());
            assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void invalidatesTheOldReceiptWhenTheTargetCreatesAnotherRoom() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            var old = scenario.rooms.membership(guest).orElseThrow();
            var action = new ExpelRoomParticipantAction(scenario.rooms);
            action.execute(owner, 2);
            scenario.leave.execute(guest, 1, 0);
            scenario.lobby(guest);
            var created = scenario.create.execute(guest, RoomScenario.command(), new RecordingRoomOutput());
            var current = scenario.rooms.membership(guest).orElseThrow();
            scenario.rooms.release(old, "LATE_OLD_CLEANUP");

            assertEquals(CreateRoomOutcome.CREATED, created.outcome());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(guest, 1, 0).outcome());
            assertEquals(current, scenario.rooms.membership(guest).orElseThrow());
            assertEquals(2, scenario.rooms.forChannel(1).size());
        }
    }

    private Map<RoomId, Room> rooms(RoomRegistry registry) throws Exception {
        var field = RoomRegistry.class.getDeclaredField("rooms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rooms = (Map<RoomId, Room>) field.get(registry);
        return rooms;
    }
}

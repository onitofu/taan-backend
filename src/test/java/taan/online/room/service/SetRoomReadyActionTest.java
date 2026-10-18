package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.OCCUPIED;
import static taan.online.room.model.RoomSlotState.READY;
import static taan.online.room.service.model.SetRoomReadyOutcome.CHANGED;
import static taan.online.room.service.model.SetRoomReadyOutcome.IGNORED;
import static taan.online.room.service.model.SetRoomReadyOutcome.REJECTED;
import static taan.online.room.service.model.SetRoomReadyOutcome.UNCHANGED;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomTeam;

class SetRoomReadyActionTest {
    @Test
    void broadcastsEveryAcceptedSetOnlyToItsRoomAndPreservesOtherGuests() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var next = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            var d = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.create.execute(foreign, RoomScenario.command(), d);
            var listings = scenario.rooms.forChannel(1);
            var action = new SetRoomReadyAction(scenario.rooms);

            assertEquals(CHANGED, action.execute(guest, true).outcome());
            assertEquals(UNCHANGED, action.execute(guest, true).outcome());
            assertEquals(List.of(true, true), a.readyValues);
            assertEquals(a.readyValues, b.readyValues);
            assertEquals(List.of(new AccountId(2), new AccountId(2)), a.readyAccounts);
            assertTrue(d.readyValues.isEmpty());
            assertSame(listings, scenario.rooms.forChannel(1));
            assertEquals("PARTICIPANT_READY", scenario.rooms.changeTeam(guest, RoomTeam.BLUE).reason());
            scenario.enter.execute(next, 1, "", c);

            assertEquals(READY, c.initializations.getFirst().slots().get(2).state());
            assertEquals(OCCUPIED, c.initializations.getFirst().slots().get(1).state());
            scenario.rooms.changeTeam(next, RoomTeam.RED);
            scenario.rooms.leave(next, 1, 0);

            assertEquals(READY, scenario.rooms.current(guest).orElseThrow().slots().get(2).state());
            assertEquals(CHANGED, action.execute(guest, false).outcome());
            assertEquals(UNCHANGED, action.execute(guest, false).outcome());
            assertTrue(scenario.rooms.changeTeam(guest, RoomTeam.BLUE).delivery().toCompletableFuture().isDone());
            assertEquals("OWNER_CANNOT_BE_READY", action.execute(owner, true).reason());
            assertEquals(false, a.readyValues.getLast());
            assertEquals(4, b.readyValues.size());
            assertEquals(UNCHANGED, action.execute(owner, false).outcome());
            assertEquals(5, b.readyValues.size());
            assertTrue(logs.text().contains("room.ready.set"));
            assertTrue(logs.text().contains("room.ready.rejected"));
            assertTrue(logs.text().contains("ready=\"true\""));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void resetsOnlyThePromotedReadyOwnerAndAcceptsItsNativeCancel() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var next = scenario.player(3, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.enter.execute(next, 1, "", c);
            scenario.rooms.setReady(guest, true);
            scenario.rooms.setReady(next, true);
            scenario.rooms.leave(owner, 1, 0);
            var snapshot = scenario.rooms.current(next).orElseThrow();

            assertEquals(next, snapshot.ownerSessionId());
            assertEquals(OCCUPIED, snapshot.slots().get(1).state());
            assertEquals(READY, snapshot.slots().get(2).state());
            assertEquals(new AccountId(3), b.readyAccounts.getLast());
            assertEquals(false, b.readyValues.getLast());
            assertEquals(b.readyValues, c.readyValues);
            assertEquals(UNCHANGED, scenario.rooms.setReady(next, false).outcome());
            assertTrue(logs.text().contains("room.ready.reset"));
        }
    }

    @Test
    void preservesReadyGuestsWhenAnUnreadyPlayerBecomesOwner() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var next = scenario.player(3, 1);
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", b);
            scenario.enter.execute(next, 1, "", c);
            scenario.rooms.setReady(guest, true);
            scenario.rooms.leave(owner, 1, 0);
            var remaining = scenario.rooms.current(next).orElseThrow();

            assertEquals(next, remaining.ownerSessionId());
            assertEquals(OCCUPIED, remaining.slots().get(1).state());
            assertEquals(READY, remaining.slots().get(2).state());
            assertEquals(List.of(true), b.readyValues);
            assertEquals(List.of(true), c.readyValues);
        }
    }

    @Test
    void includesReadyGuestsInQuickJoinPriorityAndCapacity() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var otherOwner = scenario.player(3, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.create.execute(otherOwner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);

            for (int id = 4; id <= 9; id++) {
                var next = scenario.player(id, 1);
                var output = new RecordingRoomOutput();
                scenario.quickJoin.execute(next, 65535, output);

                assertEquals(1, output.initializations.getFirst().id().value());
                scenario.rooms.setReady(next, true);
            }

            var last = scenario.player(10, 1);
            var output = new RecordingRoomOutput();
            scenario.quickJoin.execute(last, 65535, output);

            assertEquals(2, output.initializations.getFirst().id().value());
            assertEquals(8, scenario.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void revalidatesContextAndIgnoresDisconnectionAndShutdown() throws Exception {
        try (var scenario = new RoomScenario()) {
            var action = new SetRoomReadyAction(scenario.rooms);
            assertEquals(REJECTED, action.execute(UUID.randomUUID(), true).outcome());
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.channels.move(owner, new MoveToCommand(0, 2, 0, 929));

            assertEquals("CONTEXT_CHANGED", action.execute(owner, false).reason());
            scenario.channels.release(owner, "DISCONNECTED");

            assertEquals(IGNORED, action.execute(owner, false).outcome());
            scenario.rooms.close();

            assertEquals(IGNORED, action.execute(owner, true).outcome());
        }

        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            var field = RoomRegistry.class.getDeclaredField("rooms");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var rooms = (Map<RoomId, Room>) field.get(scenario.rooms);
            rooms.get(new RoomId(1)).close();

            assertEquals("ROOM_UNAVAILABLE", scenario.rooms.setReady(owner, false).reason());
        }
    }

    @Test
    void keepsAcceptedReadinessWhenDeliveryFailsAndSanitizesTheFailure() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            a.onReady = () -> {
                throw new IllegalStateException("secret@example.test");
            };
            var accepted = scenario.rooms.setReady(guest, true);
            accepted.delivery().toCompletableFuture().join();

            assertEquals(CHANGED, accepted.outcome());
            assertEquals("ROOM_NOTIFICATION_FAILED", a.closed);
            assertEquals(READY, scenario.rooms.current(guest).orElseThrow().slots().get(2).state());
            b.onReady = a.onReady;
            var failed = scenario.rooms.setReady(guest, false);

            assertThrows(RuntimeException.class, () -> failed.delivery().toCompletableFuture().join());
            assertEquals(OCCUPIED, scenario.rooms.current(guest).orElseThrow().slots().get(2).state());
            assertFalse(logs.text().contains("secret@example.test"));
            assertTrue(logs.text().contains("room.notification.failed"));
        }
    }
}

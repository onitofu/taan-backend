package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.CLOSED;
import static taan.online.room.model.RoomSlotState.OPEN;
import static taan.online.room.model.RoomTeam.BLUE;
import static taan.online.room.model.RoomTeam.RED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.CHANGED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.IGNORED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.REJECTED;
import static taan.online.room.service.model.ChangeRoomTeamOutcome.UNCHANGED;

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

class ChangeRoomTeamActionTest {
    @Test
    void changesOnlyTheActorsTeamAndBroadcastsToItsRoomWithoutPublishingListings() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var outsider = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            var d = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.create.execute(outsider, RoomScenario.command(), c);
            scenario.create.execute(foreign, RoomScenario.command(), d);
            var listings = scenario.rooms.forChannel(1);
            var membership = scenario.rooms.membership(guest).orElseThrow();
            var original = scenario.rooms.current(guest).orElseThrow().slots().get(2).participant();
            var action = new ChangeRoomTeamAction(scenario.rooms);

            assertEquals(CHANGED, action.execute(guest, BLUE).outcome());
            assertEquals(1, a.teamChanges.size());
            assertEquals(a.teamChanges, b.teamChanges);
            assertTrue(c.teamChanges.isEmpty());
            assertTrue(d.teamChanges.isEmpty());
            assertEquals(2, b.teamChanges.getFirst().source().index());
            assertEquals(1, b.teamChanges.getFirst().destination().index());
            assertSame(original, b.teamChanges.getFirst().destination().participant());
            assertEquals(UNCHANGED, action.execute(guest, BLUE).outcome());
            assertEquals(1, a.teamChanges.size());
            assertEquals(1, b.teamChanges.size());
            assertSame(listings, scenario.rooms.forChannel(1));
            assertEquals(membership, scenario.rooms.membership(guest).orElseThrow());
            assertEquals(owner, scenario.rooms.current(owner).orElseThrow().ownerSessionId());
            assertEquals(CHANGED, action.execute(owner, RED).outcome());
            assertEquals(CHANGED, action.execute(owner, BLUE).outcome());
            assertEquals(CHANGED, action.execute(guest, RED).outcome());
            assertEquals(2, scenario.rooms.forChannel(1).getFirst().playerCount());
            assertEquals(OPEN, scenario.rooms.current(guest).orElseThrow().slots().get(1).state());
            assertEquals(original, scenario.rooms.current(guest).orElseThrow().slots().get(2).participant());
            assertTrue(logs.text().contains("room.team.changed"));
            assertTrue(logs.text().contains("room.team.unchanged"));
            assertTrue(logs.text().contains("previousSlotIndex=\"2\""));
            assertTrue(logs.text().contains("team=\"BLUE\""));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void rejectsUnavailablePlacesAndCanUseTheLastOpenPlaceWithoutBalancing() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var output = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), output);
            var action = new ChangeRoomTeamAction(scenario.rooms);

            for (int slot : new int[]{2, 3, 6, 7}) {
                scenario.rooms.setSlotAvailability(owner, slot, CLOSED);
            }

            assertEquals("NO_OPEN_TEAM_SLOT", action.execute(owner, RED).reason());
            assertTrue(output.teamChanges.isEmpty());
            assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
            scenario.rooms.setSlotAvailability(owner, 7, OPEN);

            assertEquals(CHANGED, action.execute(owner, RED).outcome());
            assertEquals(7, output.teamChanges.getFirst().destination().index());
            assertEquals(owner, scenario.rooms.current(owner).orElseThrow().ownerSessionId());
            scenario.rooms.setSlotAvailability(owner, 0, CLOSED);

            assertEquals(CHANGED, action.execute(owner, BLUE).outcome());
            assertEquals(1, output.teamChanges.getLast().destination().index());
            assertEquals(CLOSED, scenario.rooms.current(owner).orElseThrow().slots().get(0).state());
            assertTrue(logs.text().contains("room.team.change.rejected"));
        }
    }

    @Test
    void rejectsInvalidTeamsMissingMembershipAndNonWaitingRooms() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            var action = new ChangeRoomTeamAction(scenario.rooms);

            assertEquals(REJECTED, action.execute(owner, null).outcome());
            assertEquals("NO_MEMBERSHIP", action.execute(UUID.randomUUID(), RED).reason());
            rooms(scenario.rooms).get(new RoomId(1)).close();

            assertEquals(REJECTED, action.execute(owner, RED).outcome());
        }
    }

    @Test
    void revalidatesChannelPhaseCharacterAndAccountBeforeChangingAnyPlace() throws Exception {
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
                    var forged = new RoomParticipant(owner, scenario.owner(other).character(), 3);
                    rooms(scenario.rooms).put(
                        created.id(),
                        new Room(created.id(), created.generation(), 1, created.settings(), null, forged)
                    );
                }

                assertEquals("CONTEXT_CHANGED", scenario.rooms.changeTeam(owner, RED).reason());
                assertTrue(output.teamChanges.isEmpty());
            }
        }
    }

    @Test
    void ignoresDisconnectedSessionsAndShutdownWithoutNewNotifications() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var output = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), output);
            scenario.channels.release(owner, "DISCONNECTED");

            assertEquals(IGNORED, scenario.rooms.changeTeam(owner, RED).outcome());
            scenario.rooms.close();

            assertEquals(IGNORED, scenario.rooms.changeTeam(owner, RED).outcome());
            assertTrue(output.teamChanges.isEmpty());
        }
    }

    @Test
    void keepsAcceptedStateAndIsolatesARecipientFailureFromTheSendersDelivery() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            b.onTeamChanged = () -> {
                throw new IllegalStateException("Private team notification failure");
            };
            var result = scenario.rooms.changeTeam(owner, RED);
            result.delivery().toCompletableFuture().join();

            assertEquals(CHANGED, result.outcome());
            assertNull(a.closed);
            assertEquals("ROOM_NOTIFICATION_FAILED", b.closed);
            assertEquals(3, a.teamChanges.getFirst().destination().index());
            assertTrue(logs.text().contains("room.notification.failed"));
            assertFalse(logs.text().contains("Private team notification failure"));
            a.onTeamChanged = b.onTeamChanged;
            var failed = scenario.rooms.changeTeam(owner, BLUE);

            assertThrows(RuntimeException.class, () -> failed.delivery().toCompletableFuture().join());
            assertEquals(CHANGED, failed.outcome());
            assertEquals(owner, scenario.rooms.current(owner).orElseThrow().slots().get(0).participant().sessionId());
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

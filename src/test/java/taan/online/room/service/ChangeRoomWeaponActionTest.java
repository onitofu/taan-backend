package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.ChangeRoomWeaponOutcome.CHANGED;
import static taan.online.room.service.model.ChangeRoomWeaponOutcome.IGNORED;
import static taan.online.room.service.model.ChangeRoomWeaponOutcome.REJECTED;
import static taan.online.room.service.model.ChangeRoomWeaponOutcome.UNCHANGED;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomTeam;

class ChangeRoomWeaponActionTest {
    @Test
    void broadcastsEveryAcceptedSetOnlyToItsRoomAndPreservesReadyGuestsAndListings() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var other = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            var d = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.create.execute(other, RoomScenario.command(), c);
            scenario.create.execute(foreign, RoomScenario.command(), d);
            var listings = scenario.rooms.forChannel(1);
            var action = new ChangeRoomWeaponAction(scenario.rooms);

            assertEquals(CHANGED, action.execute(guest, 2, 0).outcome());
            assertEquals(UNCHANGED, action.execute(guest, 2, 0).outcome());
            assertEquals(List.of(0, 0), a.weaponIds);
            assertEquals(a.weaponIds, b.weaponIds);
            assertEquals(2, a.weaponCharacters.getFirst().value());
            assertTrue(c.weaponIds.isEmpty());
            assertTrue(d.weaponIds.isEmpty());
            assertSame(listings, scenario.rooms.forChannel(1));
            scenario.rooms.setReady(guest, true);
            action.execute(owner, 1, 200);

            assertEquals(true, b.readyValues.getLast());
            assertEquals(1, b.readyValues.size());
            assertEquals("PARTICIPANT_READY", action.execute(guest, 2, 0).reason());
            scenario.rooms.setReady(guest, false);
            scenario.rooms.changeTeam(guest, RoomTeam.BLUE);

            assertEquals(0, scenario.rooms.current(guest).orElseThrow().slots().get(1).participant().weaponId());
            scenario.rooms.release(owner, "DISCONNECTED");

            assertEquals(guest, scenario.rooms.current(guest).orElseThrow().ownerSessionId());
            assertEquals(CHANGED, action.execute(guest, 2, 11).outcome());
            assertTrue(logs.text().contains("room.weapon.selected"));
            assertTrue(logs.text().contains("outcome=\"UNCHANGED\""));
            assertTrue(logs.text().contains("weaponId=\"200\""));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void rejectsForeignCharactersAndUnsupportedChoicesWithoutChangingState() throws Exception {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var output = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), output);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            var action = new ChangeRoomWeaponAction(scenario.rooms);

            for (long character : new long[]{0, 2, 1000, 0xffff_ffffL}) {
                assertEquals("CHARACTER_MISMATCH", action.execute(owner, character, 0).reason());
            }

            for (int weapon : new int[]{-1, 12, 100, 300, 65535, 65536}) {
                assertEquals("UNSUPPORTED_WEAPON", action.execute(owner, 1, weapon).reason());
            }

            assertEquals(REJECTED, action.execute(UUID.randomUUID(), 1, 0).outcome());
            rooms(scenario.rooms).get(new RoomId(1)).close();

            assertEquals("ROOM_UNAVAILABLE", action.execute(owner, 1, 0).reason());
            assertEquals(3, scenario.rooms.current(owner).orElseThrow().slots().get(0).participant().weaponId());
            assertTrue(output.weaponIds.isEmpty());
            assertTrue(logs.text().contains("room.weapon.rejected"));
        }
    }

    @Test
    void revalidatesChannelPhaseAndVerifiedIdentityAndIgnoresClosedSessions() {
        for (boolean move : new boolean[]{true, false}) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var output = new RecordingRoomOutput();
                scenario.create.execute(owner, RoomScenario.command(), output);

                if (move) {
                    scenario.channels.move(owner, new MoveToCommand(0, 2, 0, 929));
                } else {
                    scenario.lobby(owner);
                }

                assertEquals("CONTEXT_CHANGED", scenario.rooms.changeWeapon(owner, 1, 0).reason());
                assertTrue(output.weaponIds.isEmpty());
                scenario.channels.release(owner, "DISCONNECTED");

                assertEquals(IGNORED, scenario.rooms.changeWeapon(owner, 1, 0).outcome());
                scenario.rooms.close();

                assertEquals(IGNORED, scenario.rooms.changeWeapon(owner, 1, 0).outcome());
            }
        }
    }

    @Test
    void isolatesDeliveryFailuresAndKeepsTheAcceptedSelection() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            a.onWeaponChanged = () -> {
                throw new IllegalStateException("secret@example.test");
            };
            var accepted = scenario.rooms.changeWeapon(guest, 2, 0);
            accepted.delivery().toCompletableFuture().join();

            assertEquals(CHANGED, accepted.outcome());
            assertEquals("ROOM_NOTIFICATION_FAILED", a.closed);
            assertEquals(0, scenario.rooms.current(guest).orElseThrow().slots().get(2).participant().weaponId());
            b.onWeaponChanged = a.onWeaponChanged;
            var failed = scenario.rooms.changeWeapon(guest, 2, 200);

            assertThrows(RuntimeException.class, () -> failed.delivery().toCompletableFuture().join());
            assertEquals(200, scenario.rooms.current(guest).orElseThrow().slots().get(2).participant().weaponId());
            assertTrue(logs.text().contains("room.notification.failed"));
            assertFalse(logs.text().contains("secret@example.test"));
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

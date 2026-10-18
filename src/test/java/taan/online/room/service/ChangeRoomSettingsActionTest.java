package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.ChangeRoomSettingsOutcome.CHANGED;
import static taan.online.room.service.model.ChangeRoomSettingsOutcome.IGNORED;
import static taan.online.room.service.model.ChangeRoomSettingsOutcome.UNCHANGED;

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
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;

class ChangeRoomSettingsActionTest {
    @Test
    void confirmsAllApprovedCombinationsAndPublishesListingsBeforeNotificationsWithoutResettingReady() {
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
            scenario.rooms.setReady(guest, true);
            var slots = scenario.rooms.current(owner).orElseThrow().slots();
            var action = new ChangeRoomSettingsAction(scenario.rooms);
            a.onSettingsChanged = () -> {
                var listing = scenario.rooms.forChannel(1).getFirst();
                var confirmed = a.settings.getLast();
                assertEquals(confirmed.mapId(), listing.mapId());
                assertEquals(confirmed.mode(), listing.mode());
                assertEquals(confirmed.rule(), listing.rule());
            };

            for (int map : RoomScenario.maps().mapIds()) {
                for (int type : new int[]{0, 1}) {
                    int rule = type == 0 ? 2 : 0;
                    action.execute(owner, map, type, rule);
                    var listings = scenario.rooms.forChannel(1);
                    assertEquals(UNCHANGED, action.execute(owner, map, type, rule).outcome());
                    assertSame(listings, scenario.rooms.forChannel(1));
                    assertEquals(slots, scenario.rooms.current(owner).orElseThrow().slots());
                }
            }

            assertEquals(a.settings, b.settings);
            assertTrue(a.settingsRejections.stream().noneMatch(Boolean::booleanValue));
            assertTrue(c.settings.isEmpty());
            assertTrue(d.settings.isEmpty());
            assertEquals(List.of(true), b.readyValues);
            action.execute(owner, 1, 1, 0);
            assertEquals(CHANGED, action.changeMap(owner, 2).outcome());
            assertEquals(RoomMode.NO_ITEM, a.settings.getLast().mode());
            assertEquals(RoomRule.BASIC, a.settings.getLast().rule());
            action.execute(owner, 1, 0, 2);
            action.changeMap(owner, 2);

            assertEquals(RoomMode.ITEM, a.settings.getLast().mode());
            assertEquals(RoomRule.BONUS, a.settings.getLast().rule());
            assertTrue(logs.text().contains("room.settings.set"));
            assertTrue(logs.text().contains("outcome=\"UNCHANGED\""));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void resyncsOnlyTheInitiatorOnEverySemanticRejection() throws Exception {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            var action = new ChangeRoomSettingsAction(scenario.rooms);
            var original = scenario.rooms.current(owner).orElseThrow().settings();
            var listings = scenario.rooms.forChannel(1);

            assertEquals("NOT_OWNER", action.execute(guest, 2, 1, 0).reason());
            assertEquals(List.of(original), b.settings);
            assertEquals(List.of(true), b.settingsRejections);
            assertTrue(a.settings.isEmpty());

            for (int map : new int[]{0, 3, 200, 202, 300, 500, 65535}) {
                assertEquals("UNSUPPORTED_MAP", action.changeMap(owner, map).reason());
            }

            for (int[] pair : new int[][]{{-1, 0}, {0, 0}, {0, 1}, {1, 2}, {2, 0}, {3, 2}, {4, 2}, {65535, 255}}) {
                assertEquals("UNSUPPORTED_MODE_OR_RULE", action.execute(owner, 1, pair[0], pair[1]).reason());
            }

            rooms(scenario.rooms).get(new RoomId(1)).close();

            assertEquals("ROOM_UNAVAILABLE", action.execute(owner, 2, 1, 0).reason());
            assertSame(listings, scenario.rooms.forChannel(1));
            assertEquals(original, scenario.rooms.current(owner).orElseThrow().settings());
            assertTrue(a.settings.stream().allMatch(original::equals));
            assertTrue(a.settingsRejections.stream().allMatch(Boolean::booleanValue));
            assertEquals(1, b.settings.size());
            assertTrue(logs.text().contains("room.settings.rejected"));
        }
    }

    @Test
    void ignoresLostContextsWithoutReturningAnotherChannelsSettings() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var output = new RecordingRoomOutput();
            var action = new ChangeRoomSettingsAction(scenario.rooms);

            assertEquals(IGNORED, action.execute(UUID.randomUUID(), 2, 1, 0).outcome());
            scenario.create.execute(owner, RoomScenario.command(), output);
            scenario.channels.move(owner, new MoveToCommand(0, 2, 0, 929));

            assertEquals(IGNORED, action.changeMap(owner, 2).outcome());
            scenario.channels.release(owner, "DISCONNECTED");
            assertEquals(IGNORED, action.execute(owner, 2, 1, 0).outcome());
            scenario.rooms.close();
            assertEquals(IGNORED, action.changeMap(owner, 2).outcome());
            assertTrue(output.settings.isEmpty());
        }
    }

    @Test
    void preservesAcceptedSettingsWhenDeliveryFailsAndDoesNotLeakExceptions() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            a.onSettingsChanged = () -> {
                throw new IllegalStateException("secret@example.test");
            };
            var action = new ChangeRoomSettingsAction(scenario.rooms);
            var rejected = action.execute(guest, 2, 1, 0);
            rejected.delivery().toCompletableFuture().join();
            b.onSettingsChanged = a.onSettingsChanged;
            var changed = action.execute(owner, 2, 1, 0);

            assertThrows(RuntimeException.class, () -> changed.delivery().toCompletableFuture().join());
            assertEquals(CHANGED, changed.outcome());
            assertEquals(2, scenario.rooms.current(owner).orElseThrow().settings().mapId());
            assertEquals(2, scenario.rooms.forChannel(1).getFirst().mapId());
            assertEquals("ROOM_NOTIFICATION_FAILED", b.closed);
            assertFalse(logs.text().contains("secret@example.test"));
            assertTrue(logs.text().contains("room.notification.failed"));
        }
    }

    @Test
    void rejectsUnavailableMapsDuringDirectCreationWithoutAllocatingARoom() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var source = RoomScenario.settings();
            var settings = new RoomSettings(source.title(), source.mode(), source.rule(), 200, 8);
            var result = scenario.rooms.create(
                scenario.channels.membership(owner).orElseThrow(),
                settings,
                null,
                scenario.owner(owner),
                new RecordingRoomOutput()
            );

            assertEquals("UNSUPPORTED_MAP", result.reason());
            assertTrue(scenario.rooms.forChannel(1).isEmpty());
            assertTrue(scenario.rooms.membership(owner).isEmpty());
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

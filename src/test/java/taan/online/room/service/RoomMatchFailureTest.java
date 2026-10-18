package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.TestFixtures;
import taan.online.match.model.MapSpawnDefinition;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchMapDefinition;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomState;

class RoomMatchFailureTest {
    @Test
    void refusesAnUnconfiguredLifecycleWithoutReservingAnything() {
        try (
            var scenario = new RoomScenario();
            var rooms = new RoomRegistry(scenario.channels, RoomScenario.maps(), scenario.timers)
        ) {
            var owner = populate(scenario, rooms);

            assertNull(rooms.matchContext(owner));
            assertEquals("MATCH_UNAVAILABLE", rooms.reserveMatch(owner).reason());
            assertEquals(RoomState.WAITING, rooms.current(owner).orElseThrow().state());
        }
    }

    @Test
    void rollsBackPreparationFailureAndLogsItOnceWithoutCharacterOrCredentialData() {
        var definitions = MatchFixtures.definitions();
        var original = definitions.map();
        var map = new MatchMapDefinition(
            1,
            original.asset(),
            original.width(),
            original.height(),
            original.spawns().stream().map(
                value -> new MapSpawnDefinition(
                    value.index(),
                    value.rawX(),
                    value.rawY(),
                    value.settledX(),
                    value.settledY(),
                    value.side(),
                    value.flag(),
                    false
                )
            ).toList()
        );
        var runtime = MatchFixtures.registry(
            new MatchDefinitions(definitions.catalogVersion(), map, definitions.weapons())
        );

        try (
            var scenario = new RoomScenario();
            var rooms = new RoomRegistry(scenario.channels, RoomScenario.maps(), scenario.timers, runtime)
        ) {
            var owner = populate(scenario, rooms);
            var before = rooms.current(owner).orElseThrow();

            try (var logs = new LogCapture()) {
                assertThrows(IllegalStateException.class, () -> rooms.reserveMatch(owner));
                assertEquals(before, rooms.current(owner).orElseThrow());
                assertEquals(
                    1,
                    logs.events().stream().filter(event -> event.getLevel().toString().equals("ERROR")).count()
                );
                assertTrue(logs.text().contains("match.reservation.failed"));
                assertFalse(logs.text().contains("Hero"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }
        }
    }

    @Test
    void broadcastsTheCanonicalWeaponWhenReplacingLegacyRuntimeState() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var actor = scenario.owner(owner);
            var output = new RecordingRoomOutput();
            scenario.rooms.create(
                scenario.channels.membership(owner).orElseThrow(),
                RoomScenario.settings(),
                null,
                new RoomParticipant(owner, actor.character(), 3),
                output
            );
            var result = scenario.rooms.changeWeapon(owner, 1, 0);

            assertEquals("CHANGED", result.outcome().name());
            assertEquals(0, output.weaponIds.getFirst());
        }
    }

    private UUID populate(RoomScenario scenario, RoomRegistry rooms) {
        var owner = scenario.player(1, 1);
        var guest = scenario.player(2, 1);
        rooms.create(
            scenario.channels.membership(owner).orElseThrow(),
            RoomScenario.settings(),
            null,
            scenario.owner(owner),
            new RecordingRoomOutput()
        );
        var context = scenario.channels.membership(guest).orElseThrow();
        rooms.enter(
            context,
            rooms.entryTarget(context, 1).orElseThrow(),
            scenario.owner(guest),
            "",
            new RecordingRoomOutput()
        );
        rooms.setReady(guest, true);
        return owner;
    }
}

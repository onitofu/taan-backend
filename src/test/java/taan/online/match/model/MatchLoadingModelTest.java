package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomSlotState;

class MatchLoadingModelTest {
    @Test
    void validatesTheFirstActorAndWaitsForBothLoadingAndDeliveryEvidence() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var snapshot = f.scenario.matches.current(f.scenario.rooms.reserveMatch(f.owner).token()).orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> copy(snapshot, 8));
            var match = copy(snapshot, 0);
            var owner = snapshot.participants().getFirst();
            var guest = snapshot.participants().get(1);

            assertFalse(match.expired(0));
            assertEquals("FOREIGN_SLOT", match.loadingDone(UUID.randomUUID(), 0, 97, 685));
            assertEquals("INVALID_PHASE", match.loadingDone(owner.sessionId(), 0, 97, 685));
            assertFalse(match.deliveryCompleted(owner.sessionId()));
            assertTrue(match.commit(5, 30));
            assertFalse(match.expired(34));
            assertTrue(match.expired(35));
            assertFalse(match.confirmsPosition(owner.sessionId(), 0, 97, 685, 0));
            assertFalse(match.deliveryCompleted(UUID.randomUUID()));
            assertEquals("INVALID_POSITION", match.loadingDone(owner.sessionId(), 0, 97, 686));
            assertEquals("ACCEPTED", match.loadingDone(owner.sessionId(), 0, 97, 685));
            assertEquals("CONFLICTING_POSITION", match.loadingDone(owner.sessionId(), 0, 97, 686));
            assertEquals("ACCEPTED", match.loadingDone(guest.sessionId(), 2, 1110, 685));
            assertFalse(match.beginSynchronization());
            assertTrue(match.deliveryCompleted(owner.sessionId()));
            assertFalse(match.beginSynchronization());
            assertTrue(match.deliveryCompleted(guest.sessionId()));
            assertTrue(match.beginSynchronization());
            assertFalse(match.confirmsPosition(UUID.randomUUID(), 0, 97, 685, 0));
            assertFalse(match.confirmsPosition(owner.sessionId(), 0, 97, 686, 0));
            assertTrue(match.confirmsPosition(owner.sessionId(), 0, 97, 685, 0));
            assertFalse(match.previewStarted(10, 20));
            match.deliveryCompleted(owner.sessionId());
            match.deliveryCompleted(guest.sessionId());
            assertTrue(match.previewStarted(10, 20));
            assertEquals(30, match.deadlineNanos());
            assertFalse(match.deliveryCompleted(owner.sessionId()));
            assertEquals("UNCHANGED", match.loadingDone(owner.sessionId(), 0, 97, 685));
            assertThrows(UnsupportedOperationException.class, () -> match.loadedSessions().clear());
        }
    }

    @Test
    void validatesBootstrapSettingsAndRequiresAnOccupiedFrozenRoomSlot() {
        assertThrows(IllegalArgumentException.class, () -> new MatchBootstrapSettings(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MatchBootstrapSettings(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MatchBootstrapSettings(1, 1, 0));

        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var member = f.scenario.matches.current(f.scenario.rooms.reserveMatch(f.owner).token()).orElseThrow()
                .participants().getFirst();
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchParticipant(
                    member.sessionId(),
                    member.admissionId(),
                    member.slotIndex(),
                    member.team(),
                    member.character(),
                    member.weaponId(),
                    member.spawn(),
                    member.facing(),
                    member.hp(),
                    member.maxHp(),
                    member.energy(),
                    member.maxEnergy(),
                    RoomSlotState.OPEN
                )
            );
        }
    }

    @Test
    void rejectsAnInvalidTerrainSupportEvenWhenItsCoordinatesAreInsideTheMap() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var source = f.scenario.matches.current(f.scenario.rooms.reserveMatch(f.owner).token()).orElseThrow();
            var map = source.definitions().map();
            var spawns = new ArrayList<>(map.spawns());
            spawns.set(8, new MapSpawnDefinition(8, 500, 500, 500, 500, 1, 0, false));
            var definitions = new MatchDefinitions(
                source.definitions().catalogVersion(),
                new MatchMapDefinition(map.mapId(), map.asset(), map.width(), map.height(), spawns),
                source.definitions().weapons()
            );
            var match = new Match(
                source.id(),
                source.roomId(),
                source.roomGeneration(),
                source.channelId(),
                source.ownerSessionId(),
                source.mode(),
                source.rule(),
                definitions,
                source.participants(),
                source.captains(),
                0
            );
            match.commit(0, 30);

            assertEquals("INVALID_POSITION", match.loadingDone(source.ownerSessionId(), 0, 500, 500));
            assertTrue(match.loadedSessions().isEmpty());
        }
    }

    private Match copy(MatchSnapshot source, int first) {
        return new Match(
            source.id(),
            source.roomId(),
            source.roomGeneration(),
            source.channelId(),
            source.ownerSessionId(),
            source.mode(),
            source.rule(),
            source.definitions(),
            source.participants(),
            source.captains(),
            first
        );
    }
}

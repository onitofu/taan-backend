package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;

class RoomAppearanceTest {
    @Test
    void replacesOnlyAppearanceAndPreservesImmutableSnapshotsAndParticipantFields() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                null,
                scenario.owner(owner)
            );
            room.enter(scenario.owner(guest), "");
            room.changeWeapon(guest, 200);
            room.setReady(scenario.owner(guest).character().accountId(), true);
            var old = room.slots();
            var appearance = new CharacterAppearance(
                IntStream.range(0, 16).mapToObj(slot -> new AppearancePart(slot, 0, 0, 0, 0)).toList()
            );

            assertTrue(room.updateAppearance(guest, appearance));
            assertFalse(room.updateAppearance(guest, appearance));
            var changed = room.slots().get(2);
            var original = old.get(2);
            assertEquals(RoomSlotState.READY, changed.state());
            assertEquals(200, changed.participant().weaponId());
            assertEquals(original.participant().character().id(), changed.participant().character().id());
            assertEquals(original.participant().character().createdAt(), changed.participant().character().createdAt());
            assertEquals(List.of(old.getFirst()), List.of(room.slots().getFirst()));
            assertFalse(original.participant().character().appearance().equals(appearance));
            assertThrows(NoSuchElementException.class, () -> room.updateAppearance(UUID.randomUUID(), appearance));
            room.close();

            assertThrows(IllegalStateException.class, () -> room.updateAppearance(guest, appearance));
        }
    }
}

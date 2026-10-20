package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;

class RoomWeaponTest {
    @Test
    void replacesOnlyTheSelectedWeaponAndKeepsItThroughTransfersAndOwnerPromotion() {
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
            var before = room.slots();
            var character = before.get(2).participant().character();

            assertTrue(Room.isSelectableWeapon(0));
            assertFalse(room.changeWeapon(guest, 0));
            assertSame(character, room.slots().get(2).participant().character());
            assertEquals(0, before.get(2).participant().weaponId());
            room.changeTeam(guest, RoomTeam.BLUE);

            assertEquals(0, room.slots().get(1).participant().weaponId());
            room.setReady(new AccountId(2), true);
            room.leave(owner);

            assertEquals(guest, room.ownerSessionId());
            assertEquals(0, room.slots().get(1).participant().weaponId());
            assertEquals(RoomSlotState.OCCUPIED, room.slots().get(1).state());
            assertFalse(room.changeWeapon(guest, 0));
        }
    }

    @Test
    void enforcesThePickerSetPhaseReadinessAndExistingParticipant() {
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

            for (int value : new int[]{-1, 1, 2, 4, 11, 12, 100, 199, 200, 201, 300, 65535, 65536}) {
                assertFalse(Room.isSelectableWeapon(value));
                assertThrows(IllegalArgumentException.class, () -> room.changeWeapon(guest, value));
                assertThrows(IllegalArgumentException.class, () -> new RoomBootstrapDefinition(1, value));
            }

            assertThrows(IllegalArgumentException.class, () -> room.changeWeapon(UUID.randomUUID(), 0));
            room.setReady(new AccountId(2), true);

            assertThrows(IllegalStateException.class, () -> room.changeWeapon(guest, 0));
            room.setReady(new AccountId(2), false);
            room.close();

            assertThrows(IllegalStateException.class, () -> room.changeWeapon(guest, 0));
            assertEquals(0, room.slots().get(2).participant().weaponId());
        }
    }
}

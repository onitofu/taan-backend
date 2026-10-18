package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;
import taan.online.room.service.model.RoomSnapshot;

class RoomSettingsChangeTest {
    @Test
    void keepsCatalogsImmutableAndValidatesTheirIdentityRange() {
        var source = new HashSet<>(Set.of(1, 2));
        var catalog = new RoomMapCatalog(source);
        source.clear();

        assertTrue(catalog.contains(1));
        assertFalse(catalog.contains(200));
        assertThrows(UnsupportedOperationException.class, () -> catalog.mapIds().clear());
        assertThrows(IllegalArgumentException.class, () -> new RoomMapCatalog(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new RoomMapCatalog(Set.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new RoomMapCatalog(Set.of(65536)));
        assertThrows(NullPointerException.class, () -> new RoomMapCatalog(null));
        assertTrue(new RoomMapCatalog(Set.of(65535)).contains(65535));
    }

    @Test
    void changesOnlyMapModeAndRuleAndKeepsOlderSnapshotsAndReadinessIntact() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var password = new RoomPassword("private");
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                password,
                scenario.owner(owner)
            );
            room.enter(scenario.owner(guest), "private");
            room.setReady(new AccountId(2), true);
            var original = RoomSnapshot.from(room);
            var slots = room.slots();
            var maps = RoomScenario.maps();

            for (int map : maps.mapIds()) {
                for (var mode : RoomMode.values()) {
                    var rule = mode.usesItems() ? RoomRule.BONUS : RoomRule.BASIC;
                    boolean expected = room.settings().mapId() != map || room.settings().mode() != mode;
                    assertEquals(expected, room.changeSettings(mode, rule, map, maps));
                    var accepted = room.settings();

                    assertFalse(room.changeSettings(mode, rule, map, maps));
                    assertSame(accepted, room.settings());
                    assertEquals(slots, room.slots());
                    assertEquals(owner, room.ownerSessionId());
                    assertEquals(original.settings().title(), accepted.title());
                    assertEquals(8, accepted.capacity());
                    assertTrue(room.acceptsPassword("private"));
                }
            }

            assertEquals(RoomScenario.settings(), original.settings());
            assertEquals(RoomSlotState.READY, room.slots().get(2).state());
            var before = room.settings();
            assertThrows(
                IllegalArgumentException.class,
                () -> room.changeSettings(RoomMode.ITEM, RoomRule.BONUS, 200, maps)
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> room.changeSettings(RoomMode.ITEM, RoomRule.BASIC, 1, maps)
            );
            assertSame(before, room.settings());
            room.close();

            assertThrows(
                IllegalStateException.class,
                () -> room.changeSettings(RoomMode.ITEM, RoomRule.BONUS, 1, maps)
            );
        }
    }
}

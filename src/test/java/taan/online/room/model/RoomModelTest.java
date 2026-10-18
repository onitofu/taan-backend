package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

class RoomModelTest {
    @Test
    void validatesUnicodeTitlesWithoutTrimmingThemAndKeepsThemOutOfDiagnostics() {
        for (String value : List.of(
            "",
            " ",
            "\u00a0",
            "x".repeat(257),
            "a\nb",
            "a\u0000b",
            "a\u2028b",
            "a\u2029b",
            "a\ud800",
            "a\udfff"
        )) {
            assertThrows(IllegalArgumentException.class, () -> new RoomTitle(value));
        }

        assertThrows(IllegalArgumentException.class, () -> new RoomTitle(null));

        for (String value : List.of("a", "x".repeat(256), " Room 😀 ", "\u2027", "\ue000")) {
            assertEquals(value, new RoomTitle(value).value());
            assertFalse(new RoomTitle(value).toString().contains(value));
        }

        var filter = new RoomTitleFilter(List.of("BaD"));

        assertTrue(filter.forbids(new RoomTitle("Some bAD title")));
        assertFalse(filter.forbids(new RoomTitle("Ordinary room")));
    }

    @Test
    void enforcesRoomIdentityPlacementAndSettingsInvariantsAndClosesOnce() {
        assertThrows(IllegalArgumentException.class, () -> new RoomId(0));
        assertThrows(IllegalArgumentException.class, () -> new RoomId(65536));
        assertEquals(65535, new RoomId(65535).value());

        var character = CharacterFixtures.character(1, new AccountId(1), AccountGender.MALE, "Hero");
        var id = UUID.randomUUID();
        var settings = RoomScenario.settings();

        for (int[] values : List.of(new int[]{0, 3}, new int[]{65536, 3}, new int[]{1, -1}, new int[]{1, 65536})) {
            assertThrows(IllegalArgumentException.class, () -> new RoomBootstrapDefinition(values[0], values[1]));
        }

        assertEquals(200, new RoomBootstrapDefinition(65535, 200).weaponId());
        assertEquals(0, new RoomBootstrapDefinition(1, 0).weaponId());

        assertThrows(IllegalArgumentException.class, () -> new RoomParticipant(id, character, -1));
        assertThrows(IllegalArgumentException.class, () -> new RoomParticipant(id, character, 65536));
        var owner = new RoomParticipant(id, character, 65535);

        for (int[] values : List.of(new int[]{1, 7}, new int[]{0, 8}, new int[]{65536, 8})) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomSettings(settings.title(), RoomMode.ITEM, RoomRule.BONUS, values[0], values[1])
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomSettings(settings.title(), RoomMode.ITEM, RoomRule.BASIC, 1, 8)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomSettings(settings.title(), RoomMode.NO_ITEM, RoomRule.BONUS, 1, 8)
        );
        assertEquals(RoomRule.BASIC, new RoomSettings(settings.title(), RoomMode.NO_ITEM, RoomRule.BASIC, 1, 8).rule());

        for (int channel : new int[]{0, 65536}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new Room(new RoomId(1), UUID.randomUUID(), channel, settings, null, owner)
            );
        }

        var generation = UUID.randomUUID();
        var room = new Room(new RoomId(1), generation, 65535, settings, null, owner);

        assertEquals(new RoomId(1), room.id());
        assertEquals(generation, room.generation());
        assertEquals(65535, room.channelId());
        assertEquals(settings, room.settings());
        assertEquals(owner, room.owner());
        assertEquals(RoomState.WAITING, room.state());
        assertTrue(room.close());
        assertEquals(RoomState.CLOSED, room.state());
        assertFalse(room.close());
    }

    @Test
    void allocatesTheWholeWireRangeAndReusesReleasedIdsInAscendingOrder() {
        var pool = new RoomIdPool();

        for (int id = 1; id <= 65535; id++) {
            assertEquals(id, pool.allocate().orElseThrow().value());
        }

        assertTrue(pool.allocate().isEmpty());
        pool.release(new RoomId(7));
        pool.release(new RoomId(2));

        assertEquals(2, pool.allocate().orElseThrow().value());
        assertEquals(7, pool.allocate().orElseThrow().value());
        assertTrue(pool.allocate().isEmpty());
        pool.release(new RoomId(2));

        assertEquals(2, pool.allocate().orElseThrow().value());
    }

    @Test
    void reusesTheLowestFreeIdBeforeAllocatingAHigherNumber() {
        var pool = new RoomIdPool();

        assertEquals(1, pool.allocate().orElseThrow().value());
        assertEquals(2, pool.allocate().orElseThrow().value());
        assertEquals(3, pool.allocate().orElseThrow().value());

        pool.release(new RoomId(2));

        assertEquals(2, pool.allocate().orElseThrow().value());
        assertEquals(4, pool.allocate().orElseThrow().value());

        pool.release(new RoomId(3));
        pool.release(new RoomId(1));

        assertEquals(1, pool.allocate().orElseThrow().value());
        assertEquals(3, pool.allocate().orElseThrow().value());
        assertEquals(5, pool.allocate().orElseThrow().value());
    }
}

package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;

class RoomPasswordTest {
    @Test
    void validatesUtf16LimitsAndComparesTheOriginalTextWithoutNormalization() {
        assertThrows(IllegalArgumentException.class, () -> new RoomPassword(null));

        for (String value : List.of("", "x".repeat(11), "😀".repeat(5) + "x", "\ud800", "\udfff")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new RoomPassword(value));
            assertTrue(failure.getMessage().startsWith("Invalid room password"));
        }

        for (String value : List.of("x", "x".repeat(10), "😀".repeat(5), "пароль", " ".repeat(10), "\ue000")) {
            var password = new RoomPassword(value);

            assertTrue(password.matches(value));
            assertFalse(password.matches(null));
            assertFalse(password.matches(value + "x"));
            assertEquals("RoomPassword[REDACTED]", password.toString());
        }

        var password = new RoomPassword(" Ключ ");

        assertFalse(password.matches("Ключ"));
        assertFalse(password.matches(" ключ "));
        assertTrue(password.matches(" Ключ "));
        assertFalse(new RoomPassword("é").matches("e\u0301"));
    }

    @Test
    void enforcesThePasswordAtTheRoomBoundaryWithoutChangingSlotsOnFailure() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                new RoomPassword("секрет"),
                scenario.owner(owner)
            );
            var before = room.slots();

            assertTrue(room.protectedRoom());
            assertFalse(room.acceptsPassword(""));
            assertTrue(room.enter(scenario.owner(guest), "wrong").isEmpty());
            assertEquals(before, room.slots());
            assertEquals(1, room.playerCount());
            assertTrue(room.enter(scenario.owner(guest), "секрет").isPresent());
            assertEquals(2, room.playerCount());
            room.leave(owner);

            assertEquals(guest, room.ownerSessionId());
            assertTrue(room.protectedRoom());
            assertTrue(room.acceptsPassword("секрет"));
            room.leave(guest);

            assertEquals(RoomState.CLOSED, room.state());
            assertTrue(room.enter(scenario.owner(owner), "секрет").isEmpty());
        }
    }
}

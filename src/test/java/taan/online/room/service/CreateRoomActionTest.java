package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.session.model.SessionId;

class CreateRoomActionTest {
    @Test
    void rejectsUnsupportedSettingsAndFilteredTitlesBeforeReadingTheDatabase() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var id = scenario.player(1, 0);

            assertEquals(
                CreateRoomOutcome.IGNORED,
                scenario.create.execute(UUID.randomUUID(), RoomScenario.command()).outcome()
            );
            assertEquals("NO_CHANNEL", scenario.create.execute(id, RoomScenario.command()).reason());
            scenario.channels.move(id, new MoveToCommand(0, 1, 0, 929));

            assertEquals("NOT_IN_LOBBY", scenario.create.execute(id, RoomScenario.command()).reason());
            scenario.lobby(id);

            for (CreateRoomCommand command : List.of(
                new CreateRoomCommand(0, 0, "Room", 0, 0, 2, 0, 8, false, 0),
                new CreateRoomCommand(5, 1, "Room", 0, 0, 2, 0, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 1, 0, 2, 0, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 2, 2, 0, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 0, 0, 0, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 1, 2, 0, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 1, 8, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 0, 7, false, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 0, 8, true, 0),
                new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 0, 8, false, 2)
            )) {
                assertEquals("UNSUPPORTED_SETTINGS", scenario.create.execute(id, command).reason());
            }

            assertEquals("INVALID_TITLE", scenario.create.execute(id, title(null)).reason());
            assertEquals("INVALID_TITLE", scenario.create.execute(id, title(" ")).reason());
            assertEquals("TITLE_FORBIDDEN", scenario.create.execute(id, title("Private DEniED title")).reason());
            assertEquals(0, scenario.characters.transactions);
            assertFalse(logs.text().contains("Private DEniED title"));
            assertFalse(title("Private title").toString().contains("Private title"));
        }
    }

    @Test
    void loadsFreshAppearanceAndCommitsTheReadBeforePublishingTheRoom() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(10, 1);
            scenario.characters.stored.add(
                CharacterFixtures.character(1, new AccountId(10), AccountGender.FEMALE, "OtherHero")
            );
            scenario.characters.onCommit = () -> assertTrue(scenario.rooms.current(id).isEmpty());
            var result = scenario.create.execute(id, RoomScenario.command());

            assertEquals(CreateRoomOutcome.CREATED, result.outcome());
            assertEquals(scenario.characters.stored.getFirst(), result.room().owner().character());
            assertEquals(1, scenario.characters.commits);
            assertEquals(1, scenario.characters.closes);
            assertEquals(0, scenario.characters.rollbacks);
            assertEquals("ALREADY_IN_ROOM", scenario.create.execute(id, RoomScenario.command()).reason());
            assertEquals(1, scenario.characters.transactions);
            scenario.rooms.release(id, "TEST");
            scenario.lobby(id);
            scenario.characters.onCommit = () -> { };

            assertEquals(
                CreateRoomOutcome.CREATED,
                scenario.create.execute(id, new CreateRoomCommand(5, 0, "No items", 0, 1, 0, 0, 8, false, 1)).outcome()
            );
        }
    }

    @Test
    void doesNotPublishAfterDisconnectMissingCharacterChannelChangeOrPersistenceFailure() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var character = scenario.characters.stored.removeFirst();

            assertEquals("CHARACTER_UNAVAILABLE", scenario.create.execute(id, RoomScenario.command()).reason());
            assertEquals(1, scenario.characters.commits);
            assertTrue(scenario.rooms.current(id).isEmpty());
            scenario.characters.stored.add(character);
            scenario.characters.onCommit = () -> scenario.channels.move(id, new MoveToCommand(0, 2, 0, 929));

            assertEquals("CHANNEL_CHANGED", scenario.create.execute(id, RoomScenario.command()).reason());
            assertTrue(scenario.rooms.current(id).isEmpty());
            scenario.lobby(id);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(id));

            assertEquals(CreateRoomOutcome.IGNORED, scenario.create.execute(id, RoomScenario.command()).outcome());
            assertTrue(scenario.rooms.current(id).isEmpty());
        }

        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.characters.onInventories = () -> {
                throw new IllegalStateException("Private inventory value");
            };

            assertThrows(IllegalStateException.class, () -> scenario.create.execute(id, RoomScenario.command()));
            assertEquals(0, scenario.characters.commits);
            assertEquals(1, scenario.characters.rollbacks);
            assertTrue(scenario.rooms.current(id).isEmpty());
        }
    }

    @Test
    void reportsStartAsUnavailableAndDetectsBrokenMembershipContext() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);

            assertTrue(scenario.start.execute(UUID.randomUUID()).ignored());
            assertThrows(IllegalStateException.class, () -> scenario.start.execute(id));
            scenario.create.execute(id, RoomScenario.command());

            assertFalse(scenario.start.execute(id).ignored());
            assertEquals("Starting a match is currently unavailable.", scenario.start.execute(id).message());
            scenario.channels.move(id, new MoveToCommand(0, 2, 0, 929));

            assertThrows(IllegalStateException.class, () -> scenario.start.execute(id));
            scenario.channels.release(id, "TEST");

            assertThrows(IllegalStateException.class, () -> scenario.start.execute(id));
        }
    }

    private CreateRoomCommand title(String value) {
        return new CreateRoomCommand(5, 0, value, 0, 0, 2, 0, 8, false, 0);
    }
}

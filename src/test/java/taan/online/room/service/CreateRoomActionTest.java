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
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.session.model.SessionId;

class CreateRoomActionTest {
    @Test
    void rejectsUnsupportedSettingsAndFilteredTitlesBeforeReadingTheDatabase() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var id = scenario.player(1, 0);

            assertEquals(
                CreateRoomOutcome.IGNORED,
                scenario.create.execute(UUID.randomUUID(), RoomScenario.command(), new RecordingRoomOutput()).outcome()
            );
            assertEquals(
                "NO_CHANNEL",
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).reason()
            );
            scenario.channels.move(id, new MoveToCommand(0, 1, 0, 929));

            assertEquals(
                "NOT_IN_LOBBY",
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).reason()
            );
            scenario.lobby(id);

            assertEquals("UNSUPPORTED_SETTINGS", scenario.create.execute(id, null, new RecordingRoomOutput()).reason());

            for (CreateRoomCommand command : List.of(
                new CreateRoomCommand("Room", null, RoomRule.BONUS, null),
                new CreateRoomCommand("Room", RoomMode.ITEM, null, null),
                new CreateRoomCommand("Room", RoomMode.ITEM, RoomRule.BASIC, null),
                new CreateRoomCommand("Room", RoomMode.NO_ITEM, RoomRule.BONUS, null)
            )) {
                assertEquals(
                    "UNSUPPORTED_SETTINGS",
                    scenario.create.execute(id, command, new RecordingRoomOutput()).reason()
                );
            }

            assertEquals("INVALID_TITLE", scenario.create.execute(id, title(null), new RecordingRoomOutput()).reason());
            assertEquals("INVALID_TITLE", scenario.create.execute(id, title(" "), new RecordingRoomOutput()).reason());
            assertEquals(
                "TITLE_FORBIDDEN",
                scenario.create.execute(id, title("Private DEniED title"), new RecordingRoomOutput()).reason()
            );
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
            var result = scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());

            assertEquals(CreateRoomOutcome.CREATED, result.outcome());
            assertEquals(scenario.characters.stored.getFirst(), result.room().owner().character());
            assertEquals(1, scenario.characters.commits);
            assertEquals(1, scenario.characters.closes);
            assertEquals(0, scenario.characters.rollbacks);
            assertEquals(
                "ALREADY_IN_ROOM",
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).reason()
            );
            assertEquals(1, scenario.characters.transactions);
            scenario.rooms.release(id, "TEST");
            scenario.lobby(id);
            scenario.characters.onCommit = () -> { };

            assertEquals(
                CreateRoomOutcome.CREATED,
                scenario.create.execute(
                    id,
                    RoomScenario.command(5, 0, "No items", 0, 1, 0, 0, 8, null, 1),
                    new RecordingRoomOutput()
                ).outcome()
            );
        }
    }

    @Test
    void doesNotPublishAfterDisconnectMissingCharacterChannelChangeOrPersistenceFailure() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var character = scenario.characters.stored.removeFirst();

            assertEquals(
                "CHARACTER_UNAVAILABLE",
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).reason()
            );
            assertEquals(1, scenario.characters.commits);
            assertTrue(scenario.rooms.current(id).isEmpty());
            scenario.characters.stored.add(character);
            scenario.characters.onCommit = () -> scenario.channels.move(id, new MoveToCommand(0, 2, 0, 929));

            assertEquals(
                "CHANNEL_CHANGED",
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).reason()
            );
            assertTrue(scenario.rooms.current(id).isEmpty());
            scenario.lobby(id);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(id));

            assertEquals(
                CreateRoomOutcome.IGNORED,
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).outcome()
            );
            assertTrue(scenario.rooms.current(id).isEmpty());
        }

        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.characters.onInventories = () -> {
                throw new IllegalStateException("Private inventory value");
            };

            assertThrows(
                IllegalStateException.class,
                () -> scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput())
            );
            assertEquals(0, scenario.characters.commits);
            assertEquals(1, scenario.characters.rollbacks);
            assertTrue(scenario.rooms.current(id).isEmpty());
        }
    }

    @Test
    void reportsStartAsUnavailableAndDetectsBrokenMembershipContext() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);

            assertEquals(RoomStartOutcome.IGNORED, scenario.start.execute(UUID.randomUUID()).outcome());
            assertEquals(RoomStartOutcome.IGNORED, scenario.start.execute(id).outcome());
            scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());

            assertEquals(RoomStartOutcome.REJECTED, scenario.start.execute(id).outcome());
            assertEquals("MISSING_TEAM", scenario.start.execute(id).reason());
            scenario.channels.move(id, new MoveToCommand(0, 2, 0, 929));

            assertEquals(RoomStartOutcome.IGNORED, scenario.start.execute(id).outcome());
            scenario.channels.release(id, "TEST");

            assertEquals(RoomStartOutcome.IGNORED, scenario.start.execute(id).outcome());
        }
    }

    private CreateRoomCommand title(String value) {
        return RoomScenario.command(5, 0, value, 0, 0, 2, 0, 8, null, 0);
    }
}

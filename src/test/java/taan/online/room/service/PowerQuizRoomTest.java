package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.view.RoomRuleMapper;
import taan.online.room.view.RoomSnapshotMapper;

class PowerQuizRoomTest {
    @Test
    void appliesTheExactSubstringOnlyToItemModesAndPublishesTheSameRuleInTheLobbyAndRoom() {
        for (String title : List.of(
            "PowerQuiz",
            "before PowerQuiz after",
            "PowerQuiz suffix",
            "prefixPowerQuiz",
            "powerquiz",
            "POWERQUIZ",
            "Power Quiz",
            "Room"
        )) {
            for (int type = 0; type < 4; type++) {
                try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
                    var id = scenario.player(1, 1);
                    var output = new RecordingRoomOutput();
                    var result = scenario.create.execute(
                        id,
                        new CreateRoomCommand(5, 0, title, 0, type, type % 2 == 0 ? 2 : 0, 0, 8, null, 0),
                        output
                    );
                    int expected = type % 2 != 0 ? 0 : title.contains("PowerQuiz") ? 1 : 2;

                    assertEquals(CreateRoomOutcome.CREATED, result.outcome());
                    assertEquals(expected, new RoomRuleMapper().map(result.room().settings().rule()));
                    assertEquals(expected, new RoomSnapshotMapper().map(output.initializations.getFirst())
                        .info().details().rule());
                    assertEquals(expected, new LobbyRoomListMapper().map(scenario.rooms.forChannel(1).getFirst())
                        .rule());
                    assertFalse(logs.text().contains("title="));
                    assertFalse(logs.text().contains("before PowerQuiz after"));
                }
            }
        }
    }

    @Test
    void preservesTheRuleAcrossMapChangesAndClientDefaultsAndRestoresItAfterNoItem() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var output = new RecordingRoomOutput();
            var created = scenario.create.execute(
                id,
                new CreateRoomCommand(5, 0, "PowerQuiz", 0, 0, 1, 0, 8, null, 0),
                output
            );
            assertEquals(RoomRule.POWER_QUIZ, created.room().settings().rule());
            assertEquals(RoomRule.POWER_QUIZ, scenario.rooms.changeMap(id, 2).settings().rule());

            for (int rule : new int[]{2, 1}) {
                assertEquals(RoomRule.POWER_QUIZ, scenario.rooms.changeSettings(id, 1, 2, rule).settings().rule());
                assertEquals(RoomRule.POWER_QUIZ, output.settings.getLast().rule());
            }

            assertEquals(RoomRule.BASIC, scenario.rooms.changeSettings(id, 1, 1, 0).settings().rule());
            assertEquals(
                ChangeRoomSettingsOutcome.REJECTED,
                scenario.rooms.changeSettings(id, 1, 1, 1).outcome()
            );
            assertEquals(RoomRule.POWER_QUIZ, scenario.rooms.changeSettings(id, 1, 0, 2).settings().rule());
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomSettings(new RoomTitle("PowerQuiz"), RoomMode.NO_ITEM, RoomRule.POWER_QUIZ, 1, 8)
            );
        }
    }

    @Test
    void rejectsWireRuleOneWithoutTheServerConditionBeforeLoadingTheCharacter() {
        for (String title : List.of("Room", "powerquiz", "PowerQuiz")) {
            try (var scenario = new RoomScenario()) {
                var id = scenario.player(1, 1);
                var output = new RecordingRoomOutput();
                int type = title.equals("PowerQuiz") ? 1 : 0;
                var rejected = scenario.create.execute(
                    id,
                    new CreateRoomCommand(5, 0, title, 0, type, 1, 0, 8, null, 0),
                    output
                );
                assertEquals(CreateRoomOutcome.REJECTED, rejected.outcome());
                assertEquals(0, scenario.characters.transactions);
            }
        }
    }
}

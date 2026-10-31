package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomRulePolicy;
import taan.online.room.model.RoomTitle;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;

class RoomSettingsMapperTest {
    @Test
    void mapsKnownWireValuesAndKeepsUnsupportedSelectionsExplicit() {
        var modes = List.of(RoomMode.ITEM, RoomMode.NO_ITEM, RoomMode.CAPTAIN_ITEM, RoomMode.CAPTAIN_NO_ITEM);
        var rules = List.of(RoomRule.BASIC, RoomRule.POWER_QUIZ, RoomRule.BONUS);

        for (int type = 0; type < modes.size(); type++) {
            for (int rule = 0; rule < rules.size(); rule++) {
                var command = RoomSettingsMapper.settings(2, type, rule);
                assertEquals(2, command.mapId());
                assertEquals(modes.get(type), command.mode());
                assertEquals(rules.get(rule), command.rule());
            }
        }

        assertNull(RoomSettingsMapper.settings(1, -1, 0).mode());
        assertNull(RoomSettingsMapper.settings(1, 4, 0).mode());
        assertNull(RoomSettingsMapper.settings(1, 0, 3).rule());
        assertFalse(RoomRulePolicy.permits(new RoomTitle("Room"), RoomMode.ITEM, null));
        var command = RoomSettingsMapper.create(info(5, 0, 0, 0, 8, 1));
        assertEquals("Room", command.title());
        assertEquals(RoomMode.ITEM, command.mode());
        assertEquals(RoomRule.BONUS, command.rule());
        assertNull(command.password());
    }

    @Test
    void rejectsUnsupportedCreationMetadataBeforeInvokingTheApplication() {
        assertNull(RoomSettingsMapper.create(new GameSessionInfo(0, 5, null)));

        for (var info : List.of(
            info(0, 0, 0, 0, 8, 0),
            info(5, 1, 0, 0, 8, 0),
            info(5, 0, 1, 0, 8, 0),
            info(5, 0, 0, 1, 8, 0),
            info(5, 0, 0, 0, 7, 0),
            info(5, 0, 0, 0, 8, -1),
            info(5, 0, 0, 0, 8, 2)
        )) {
            assertNull(RoomSettingsMapper.create(info));
        }
    }

    private GameSessionInfo info(int state, int grade, int kind, int count, int capacity, int map) {
        return new GameSessionInfo(
            0,
            state,
            new GameSessionDetails(grade, "Room", kind, 0, 2, count, capacity, null, map)
        );
    }
}

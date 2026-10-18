package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomScenario.map;
import static taan.online.RoomScenario.settings;

import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PqGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeMap;

class ChangeRoomSettingsPacketHandlerTest {
    @Test
    void mapsFullAndMapOnlyRequestsAndIgnoresTheMissionByte() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            int type = PqGameSessionChangeGameOption.TYPE_ID;

            for (int ignored : new int[]{0, 2, 255}) {
                assertEquals(
                    RoomReplyOutcome.SETTINGS_UPDATED,
                    scenario.handle(owner, type, settings(2, 1, 0, ignored)).outcome()
                );
            }

            assertEquals(
                RoomReplyOutcome.SETTINGS_REJECTED,
                scenario.handle(owner, type, settings(200, 1, 0, 255)).outcome()
            );
            assertEquals(
                RoomReplyOutcome.SETTINGS_UPDATED,
                scenario.handle(owner, PqGameSessionChangeMap.TYPE_ID, map(1)).outcome()
            );
            scenario.channels.release(owner, "DISCONNECTED");

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handle(owner, PqGameSessionChangeMap.TYPE_ID, map(2)).outcome()
            );
        }
    }
}

package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.weapon;

import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PqGameSessionChangeWeapon;

class ChangeRoomWeaponPacketHandlerTest {
    @Test
    void mapsAcceptedRejectedAndIgnoredResultsWithoutAdditionalReplies() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            int type = PqGameSessionChangeWeapon.TYPE_ID;

            assertEquals(RoomReplyOutcome.WEAPON_UPDATED, scenario.handle(owner, type, weapon(1, 0)).outcome());
            assertEquals(RoomReplyOutcome.WEAPON_UPDATED, scenario.handle(owner, type, weapon(1, 0)).outcome());
            assertEquals(RoomReplyOutcome.WEAPON_REJECTED, scenario.handle(owner, type, weapon(2, 0)).outcome());
            assertEquals(RoomReplyOutcome.WEAPON_REJECTED, scenario.handle(owner, type, weapon(1, 100)).outcome());
            assertTrue(scenario.lastOutput.batches.isEmpty());
            scenario.channels.release(owner, "DISCONNECTED");

            assertEquals(RoomReplyOutcome.IGNORED, scenario.handle(owner, type, weapon(1, 0)).outcome());
        }
    }
}

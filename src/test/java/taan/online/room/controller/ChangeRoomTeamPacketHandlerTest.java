package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.team;

import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.model.RoomSlotState;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionCreate;

class ChangeRoomTeamPacketHandlerTest {
    @Test
    void mapsTeamChangesAndSilentlyIgnoresRepeatsAndInvalidValues() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            int type = PqGameSessionChangeTeam.TYPE_ID;

            assertEquals(RoomReplyOutcome.TEAM_CHANGED, scenario.handle(guest, type, team(1)).outcome());
            assertEquals(RoomReplyOutcome.TEAM_UNCHANGED, scenario.handle(guest, type, team(1)).outcome());
            assertTrue(scenario.lastOutput.batches.isEmpty());
            assertEquals(RoomReplyOutcome.TEAM_CHANGED, scenario.handle(guest, type, team(2)).outcome());

            for (int value : new int[]{0, 3, 255}) {
                assertEquals(RoomReplyOutcome.TEAM_REJECTED, scenario.handle(guest, type, team(value)).outcome());
                assertTrue(scenario.lastOutput.batches.isEmpty());
            }

            scenario.channels.release(guest, "DISCONNECTED");

            assertEquals(RoomReplyOutcome.IGNORED, scenario.handle(guest, type, team(1)).outcome());
        }
    }

    @Test
    void showsOnlyTheInitiatorAFixedNoticeWhenTheTargetTeamHasNoOpenPlace() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            scenario.handle(owner, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            var output = scenario.lastOutput;

            for (int slot : new int[]{2, 3, 6, 7}) {
                scenario.rooms.setSlotAvailability(owner, slot, RoomSlotState.CLOSED);
            }

            int before = output.batches.size();
            var reply = scenario.handle(owner, PqGameSessionChangeTeam.TYPE_ID, team(2));

            assertEquals(RoomReplyOutcome.TEAM_REJECTED, reply.outcome());
            assertEquals(
                "No open slots on the requested team.",
                new NoticeMessageCodec().decode(scenario.lastOutput.batches.getFirst().getFirst()).message()
            );
            assertEquals(before, output.batches.size());
            assertEquals(owner, scenario.rooms.current(owner).orElseThrow().slots().get(0).participant().sessionId());
        }
    }
}

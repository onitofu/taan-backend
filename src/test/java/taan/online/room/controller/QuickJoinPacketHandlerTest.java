package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.session.model.SessionId;

class QuickJoinPacketHandlerTest {
    @Test
    void repliesWithAnEmptyFailureThenACompleteRosterAndRefusesARepeatedAdmission() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var failed = scenario.handle(guest, PqGameSessionQuickJoin.TYPE_ID, root("quickjoin-any"));

            assertEquals(RoomReplyOutcome.QUICK_JOIN_REJECTED, failed.outcome());
            assertArrayEquals(root("quickjoin-fail"), scenario.lastOutput.batches.getFirst().getFirst());
            scenario.handle(owner, 0x50a707d2, root("create-unprotected"));
            var ownerOutput = scenario.lastOutput;
            var joined = scenario.handle(guest, PqGameSessionQuickJoin.TYPE_ID, root("quickjoin-any"));
            var init = new RoomResponseCodec().initialize(scenario.lastOutput.batches.getFirst().getFirst());

            assertEquals(RoomReplyOutcome.ENTERED, joined.outcome());
            assertEquals(8, init.slots().size());
            assertEquals(2, init.info().details().playerCount());
            assertEquals(2, init.slots().get(2).occupant().characterId());
            var update = new RoomResponseCodec().slot(ownerOutput.batches.getLast().getFirst());

            assertEquals(init.slots().get(2), update.slot());
            scenario.handle(guest, PqGameSessionQuickJoin.TYPE_ID, root("quickjoin-any"));

            assertArrayEquals(root("quickjoin-fail"), scenario.lastOutput.batches.getFirst().getFirst());
            assertEquals(2, scenario.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void suppressesTheReplyAndAdmissionWhenTheSessionClosesDuringLoading() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(id));
            var result = scenario.handle(id, PqGameSessionQuickJoin.TYPE_ID, root("quickjoin-any"));

            assertEquals(RoomReplyOutcome.IGNORED, result.outcome());
            assertTrue(scenario.lastOutput.batches.isEmpty());
            assertTrue(scenario.rooms.membership(id).isEmpty());
        }
    }
}

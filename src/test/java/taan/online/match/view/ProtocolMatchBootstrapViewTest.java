package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.SetPosCodec;

class ProtocolMatchBootstrapViewTest {
    @Test
    void encodesTheApprovedProfileAgainstIndependentGoldenBytesAndPreservesTheFrozenRoster() throws Exception {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            f.scenario.rooms.commitMatch(token);
            var snapshot = f.scenario.matches.current(token).orElseThrow();
            var output = new CollectingPacketOutput();
            var view = new ProtocolMatchView(output);
            view.start(snapshot);

            assertEquals(3, output.batches.getFirst().size());
            var codec = new RoomResponseCodec();

            for (int index = 0; index < 2; index++) {
                var slot = codec.slot(output.batches.getFirst().get(index)).slot();
                var member = snapshot.participants().get(index);
                assertEquals(member.slotIndex(), slot.slotIndex());
                assertEquals(member.character().name().value(), slot.occupant().name());
                assertEquals(member.character().id().value(), slot.occupant().characterId());
                assertTrue(slot.occupant().options() != null);
                assertEquals(index == 0 ? 2 : 3, slot.state());
            }

            assertArrayEquals(MatchFixtures.root("start-cannon-two-player-policy"), output.batches.getFirst().get(2));
            MatchFixtures.loaded(f.scenario.matches, token);
            view.firstTurn(f.scenario.matches.current(token).orElseThrow(), f.owner);

            assertEquals(2, output.batches.size());
            assertEquals(2, new SetPosCodec().decode(output.batches.getLast().getFirst()).slotIndex());
            assertArrayEquals(
                MatchFixtures.root("first-turn-slot2-grant0-policy-example"),
                output.batches.getLast().getLast()
            );
        }
    }

    @Test
    void encodesAllThreeTeamLeaderBytesFromTheFrozenCaptainSelection() {
        for (var mode : new RoomMode[]{RoomMode.CAPTAIN_ITEM, RoomMode.CAPTAIN_NO_ITEM}) {
            try (var f = new MatchRoomFixture(8, mode)) {
                f.captains();
                var token = f.scenario.rooms.reserveMatch(f.owner).token();
                var snapshot = f.scenario.matches.current(token).orElseThrow();
                var output = new CollectingPacketOutput();
                new ProtocolMatchView(output).start(snapshot);
                var start = new StartGameCodec().decode(output.batches.getLast().getLast());

                assertEquals(255, start.teamLeaderSlots().getFirst());
                assertEquals(snapshot.captains().blueLeaderSlot(), start.teamLeaderSlots().get(1));
                assertEquals(snapshot.captains().redLeaderSlot(), start.teamLeaderSlots().get(2));
                assertEquals(8, start.players().size());
            }
        }
    }
}

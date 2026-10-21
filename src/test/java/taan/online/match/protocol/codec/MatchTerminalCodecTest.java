package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.match.protocol.packet.PcGameSessionEndGame;

class MatchTerminalCodecTest {
    @Test
    void matchesFullNoAwardResultsAndOutgoingGiveUpAndCardSelectionBytes() throws Exception {
        var ends = new EndGameCodec();
        var end = MatchWireFixtures.golden("end-game-result0-no-awards.root.hex", ends::decode, ends::encode);

        assertEquals(0, end.result().resultKind());
        assertEquals(8, end.result().slots().size());
        assertEquals(406, ends.encode(end).length);

        for (int index = 0; index < 8; index++) {
            assertEquals(index, end.result().slots().get(index).slotIndex());
            assertEquals(0, end.result().slots().get(index).accuracy());
        }

        var giveUp = new GiveUpCodec();
        var leave = new LeaveUserCodec();
        var cards = new ResultSelectionCodec();
        MatchWireFixtures.golden("give-up-empty.root.hex", giveUp::decode, giveUp::encode);
        var departed = MatchWireFixtures.golden("leave-user-slot2.root.hex", leave::decode, leave::encode);
        var selected = MatchWireFixtures.golden("PqResult_SelectCard.root.hex", cards::selectCard, cards::encode);
        var random = MatchWireFixtures.golden(
            "PqResult_RandSelect.captured.root.hex",
            cards::randomSelect,
            cards::encode
        );

        assertEquals(2, departed.slotIndex());
        assertEquals(0, selected.scalar10());
        assertEquals(2, selected.scalar11());
        assertEquals(2, random.scalar10());
    }

    @Test
    void preservesResultScalarsFloatsAndImmutableTriplesWithoutInventingInlineOptionWrappers() throws Exception {
        var codec = new EndGameCodec();
        var base = codec.decode(MatchWireFixtures.fixture("end-game-result0-no-awards.root.hex"));
        var values = new ArrayList<>(List.of(1, 2, 255));
        var slots = new ArrayList<>(base.result().slots());
        slots.set(0, new GameResultSlot(0, 65535, 0xffffffffL, 123, 255, 456, -1.25f, 789, values, 255, 12, 34));
        var message = new PcGameSessionEndGame(new GameResult(2, slots, 0xffffffffL, 42), 255);
        values.clear();
        slots.clear();

        assertEquals(message, codec.decode(codec.encode(message)));
        assertEquals(List.of(1, 2, 255), message.result().slots().getFirst().medals());
        assertThrows(UnsupportedOperationException.class, () -> message.result().slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> message.result().slots().getFirst().medals().clear());
    }
}

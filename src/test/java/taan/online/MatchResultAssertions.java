package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.packet.PcGameSessionEndGame;

public final class MatchResultAssertions {
    private MatchResultAssertions() {
    }

    public static PcGameSessionEndGame readEnd(MatchResultReader reader, int winner) throws Exception {
        var rewards = new SetRewardCodec().decode(reader.read());
        var end = new EndGameCodec().decode(reader.read());
        assertEquals(winner, end.result().resultKind());
        assertEquals(8, end.result().slots().size());
        assertEquals(0, end.packetFlag());

        for (int index = 0; index < 8; index++) {
            var reward = rewards.rewards().get(index);
            var slot = end.result().slots().get(index);
            assertEquals(index, slot.slotIndex());
            assertEquals(0, reward.amount());
            assertEquals("", reward.label());
            assertEquals(0, slot.expDeltaFallback());
            assertEquals(0, slot.displayedGold());
            assertEquals(0, slot.medals().get(1));
            assertEquals(0, slot.medals().get(2));
            assertEquals(slot.level() > 0, reward.characterId() > 0);
            assertTrue(slot.medals().getFirst() == 0 || slot.medals().getFirst() == 1);
        }

        return end;
    }
}

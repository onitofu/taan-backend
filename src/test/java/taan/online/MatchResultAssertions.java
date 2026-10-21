package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.character.protocol.packet.PcUpdateExp;
import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.PcGameSessionEndGame;

public final class MatchResultAssertions {
    private MatchResultAssertions() {
    }

    public static PcGameSessionEndGame readEnd(MatchResultReader reader, int winner) throws Exception {
        var first = reader.read();

        if (ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).getInt() == PcUpdateExp.TYPE_ID) {
            var progress = new UpdateExpCodec().decode(first);
            assertEquals(0, progress.localIndex());
            assertTrue(progress.nextLevelExp() > progress.levelStartExp());
            first = reader.read();
        }

        if (ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).getInt() == PcUpdateMoney.TYPE_ID) {
            assertTrue(new UpdateMoneyCodec().decode(first).balance() >= 0);
            first = reader.read();
        }

        var rewards = new SetRewardCodec().decode(first);
        var end = new EndGameCodec().decode(reader.read());
        assertEquals(winner, end.result().resultKind());
        assertEquals(8, end.result().slots().size());
        assertEquals(0, end.packetFlag());

        for (int index = 0; index < 8; index++) {
            var reward = rewards.rewards().get(index);
            var slot = end.result().slots().get(index);
            assertEquals(index, slot.slotIndex());
            assertTrue(reward.amount() >= 0);
            assertEquals("", reward.label());
            if (slot.expSnapshots().isEmpty()) {
                assertEquals(0, slot.expDeltaFallback());
            } else {
                assertEquals(
                    slot.expSnapshots().getLast().currentExp() - slot.expSnapshots().getFirst().currentExp(),
                    (int) slot.expDeltaFallback()
                );
                assertEquals(slot.level(), slot.expSnapshots().getLast().level());
            }
            assertTrue(slot.displayedGold() >= reward.amount() || reward instanceof GameRewardExp);
            assertTrue(slot.accuracy() >= 0 && slot.accuracy() <= 1);
            assertTrue(slot.medals().get(1) == 0 || slot.medals().get(1) == 1);
            assertEquals(0, slot.medals().get(2));
            assertEquals(slot.level() > 0, reward.characterId() > 0);
            assertTrue(slot.medals().getFirst() == 0 || slot.medals().getFirst() == 1);
        }

        return end;
    }
}

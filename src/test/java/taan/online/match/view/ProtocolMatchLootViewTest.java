package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchFixtures;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchBonusDelivery;

class ProtocolMatchLootViewTest {
    @Test
    void reproducesCapturedBonusBytesWithoutNormalizingOpaqueHeaderValuesAndEncodesSafeDiscard() throws Exception {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output);
        view.bonus(new MatchBonusDelivery(0, 5, 0, 5, 0x00676e70)).toCompletableFuture().join();
        assertArrayEquals(
            MatchFixtures.root("pending-03-PcGamePlay_EatBonusItem"),
            output.batches.getFirst().getFirst()
        );
        view.bonus(new MatchBonusDelivery(2, 0x3f020000, 0, 7, 0)).toCompletableFuture().join();
        assertArrayEquals(
            MatchFixtures.root("pending-04-PcGamePlay_EatBonusItem"),
            output.batches.getLast().getFirst()
        );
        view.discard(new DiscardMatchItemCommand(null, 0, 1, 1005)).toCompletableFuture().join();
        assertArrayEquals(
            MatchFixtures.root("forsake-known-slot1-actor0.synthetic"),
            output.batches.getLast().getFirst()
        );
        assertEquals(3, output.batches.size());
    }
}

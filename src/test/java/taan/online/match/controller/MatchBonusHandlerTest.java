package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;

class MatchBonusHandlerTest {
    @Test
    void parsesNativeAndGaugeReportsWithoutSendingAwardsAndIgnoresMissingRequestContext() throws Exception {
        try (var f = new MatchItemHandlerFixture()) {
            var snapshot = f.snapshot();
            f.owner.batches.clear();
            f.guest.batches.clear();
            for (String name : new String[]{
                "pending-03-PcGamePlay_EatBonusItem",
                "pending-04-PcGamePlay_EatBonusItem",
                "EatBonusItem-with-gauge.synthetic"
            }) {
                byte[] root = MatchFixtures.root(name);
                var member = snapshot.participants().stream().filter(p -> p.slotIndex() == root[4])
                    .findFirst().orElseThrow();
                f.handle(
                    member.sessionId(),
                    PcGamePlayEatBonusItem.TYPE_ID,
                    root,
                    f.handler.capture(member.sessionId())
                );
            }

            f.handler.handle(f.room.owner, PcGamePlayEatBonusItem.TYPE_ID, new byte[0], null)
                .toCompletableFuture().join();
            assertEquals(snapshot, f.snapshot());
            assertTrue(f.owner.batches.isEmpty());
            assertTrue(f.guest.batches.isEmpty());
            assertTrue(f.owner.closeReasons.isEmpty());
            assertTrue(f.guest.closeReasons.isEmpty());
        }
    }
}

package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayEatItemBox;
import taan.online.match.protocol.packet.PcGamePlayForsakeItem;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.service.ActivateSpecialAction;
import taan.online.match.service.ConfirmMatchItemReceiptAction;
import taan.online.match.service.DiscardMatchItemAction;
import taan.online.match.service.RecordMatchSpendAction;
import taan.online.match.service.ReportMatchBonusAction;
import taan.online.match.service.ReportMatchPickupAction;
import taan.online.protocol.MalformedPacketException;

class MatchLootHandlerTest {
    @Test
    void mapsEveryMessageToItsOwnerWithoutEchoingAndRejectsUnknownItemTypes() throws Exception {
        try (var f = new MatchItemHandlerFixture()) {
            var matches = f.room.scenario.matches;
            var handler = new MatchItemPacketHandler(
                new ActivateSpecialAction(matches),
                new RecordMatchSpendAction(matches),
                new ReportMatchBonusAction(matches),
                new ReportMatchPickupAction(matches),
                new ConfirmMatchItemReceiptAction(matches),
                new DiscardMatchItemAction(matches)
            );
            var member = f.snapshot().participants().stream().filter(p -> p.slotIndex() == 0).findFirst().orElseThrow();
            var captured = f.handler.capture(member.sessionId());
            var before = f.snapshot();
            f.owner.batches.clear();
            f.guest.batches.clear();

            for (String name : new String[]{"EatItemBox-plain.synthetic", "EatItemBox-with-gauge.synthetic"}) {
                f.handle(member.sessionId(), PcGamePlayEatItemBox.TYPE_ID, MatchFixtures.root(name), captured);
            }

            f.handle(
                member.sessionId(),
                PcGamePlayNotifyEatItem.TYPE_ID,
                MatchFixtures.root("notify-slot0-power-actor0.synthetic"),
                captured
            );
            f.handle(
                member.sessionId(),
                PcGamePlayForsakeItem.TYPE_ID,
                MatchFixtures.root("forsake-known-slot1-actor0.synthetic"),
                captured
            );

            assertEquals(before, f.snapshot());
            assertTrue(f.owner.batches.isEmpty());
            assertTrue(f.guest.batches.isEmpty());
            assertTrue(f.owner.closeReasons.isEmpty());
            assertTrue(f.guest.closeReasons.isEmpty());
            assertThrows(
                MalformedPacketException.class,
                () -> handler.handle(member.sessionId(), 0x12345678, new byte[0], captured)
            );
            handler.handle(member.sessionId(), PcGamePlayEatItemBox.TYPE_ID, new byte[0], null)
                .toCompletableFuture().join();
            handler.handle(UUID.randomUUID(), PcGamePlayNotifyEatItem.TYPE_ID, new byte[0], captured)
                .toCompletableFuture().join();
        }
    }
}

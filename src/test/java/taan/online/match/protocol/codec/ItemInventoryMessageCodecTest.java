package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayForsakeItem;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.protocol.MalformedPacketException;

class ItemInventoryMessageCodecTest {
    private final NotifyEatItemCodec receipts = new NotifyEatItemCodec();
    private final ForsakeItemCodec discards = new ForsakeItemCodec();

    @Test
    void matchesIndependentNotifyAndForsakeRootsForBothActors() throws Exception {
        for (int actor : new int[]{0, 2}) {
            byte[] notify = MatchFixtures.root("notify-slot0-power-actor" + actor + ".synthetic");
            byte[] discard = MatchFixtures.root("forsake-known-slot1-actor" + actor + ".synthetic");

            assertEquals(new PcGamePlayNotifyEatItem(actor, 0, 5), receipts.decode(notify));
            assertEquals(new PcGamePlayForsakeItem(actor, 1, 1005), discards.decode(discard));
            assertArrayEquals(notify, receipts.encode(receipts.decode(notify)));
            assertArrayEquals(discard, discards.encode(discards.decode(discard)));
        }

        var receipt = new PcGamePlayNotifyEatItem(7, 2, 65535);
        var discard = new PcGamePlayForsakeItem(7, 2, 65535);
        assertEquals(receipt, receipts.decode(receipts.encode(receipt)));
        assertEquals(discard, discards.decode(discards.encode(discard)));
    }

    @Test
    void rejectsMalformedRootsAndInvalidSlotsAndIndices() throws Exception {
        byte[] notify = MatchFixtures.root("notify-slot0-power-actor0.synthetic");
        byte[] discard = MatchFixtures.root("forsake-known-slot1-actor0.synthetic");

        for (int size = 0; size < 8; size++) {
            byte[] shortNotify = Arrays.copyOf(notify, size);
            byte[] shortDiscard = Arrays.copyOf(discard, size);
            assertThrows(MalformedPacketException.class, () -> receipts.decode(shortNotify));
            assertThrows(MalformedPacketException.class, () -> discards.decode(shortDiscard));
        }

        assertThrows(MalformedPacketException.class, () -> receipts.decode(discard));
        assertThrows(MalformedPacketException.class, () -> discards.decode(notify));
        assertThrows(MalformedPacketException.class, () -> receipts.decode(Arrays.copyOf(notify, 9)));
        assertThrows(MalformedPacketException.class, () -> discards.decode(Arrays.copyOf(discard, 9)));

        for (int offset : new int[]{4, 5}) {
            byte[] wrongNotify = notify.clone();
            byte[] wrongDiscard = discard.clone();
            wrongNotify[offset] = 8;
            wrongDiscard[offset] = 8;
            assertThrows(MalformedPacketException.class, () -> receipts.decode(wrongNotify));
            assertThrows(MalformedPacketException.class, () -> discards.decode(wrongDiscard));
        }

        assertThrows(MalformedPacketException.class, () -> receipts.encode(new PcGamePlayNotifyEatItem(8, 0, 5)));
        assertThrows(MalformedPacketException.class, () -> discards.encode(new PcGamePlayForsakeItem(8, 0, 5)));

        for (int index : new int[]{-1, 3}) {
            assertThrows(
                MalformedPacketException.class,
                () -> receipts.encode(new PcGamePlayNotifyEatItem(0, index, 5))
            );
            assertThrows(
                MalformedPacketException.class,
                () -> discards.encode(new PcGamePlayForsakeItem(0, index, 5))
            );
        }
    }
}

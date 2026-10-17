package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.inventory.protocol.codec.InventoryUpdateCodec;
import taan.online.inventory.protocol.codec.MoveInventoryItemRequestCodec;
import taan.online.inventory.protocol.packet.Inventory;
import taan.online.inventory.protocol.packet.InventoryItemData;
import taan.online.inventory.protocol.packet.PcUpdateInventory;
import taan.online.inventory.protocol.packet.PrMoveInventoryItem;
import taan.online.protocol.MalformedPacketException;

class InventoryWireTest {
    @Test
    void matchesIndependentRequestAndSnapshotBytesWithoutTruncatingInstanceIds() throws Exception {
        var codec = new MoveInventoryItemRequestCodec();
        byte[] root = root("move-swap-bag-to-equipment");
        var request = codec.decode(root);

        assertEquals(1, request.accountId());
        assertEquals(10010, request.sourceInventoryId());
        assertEquals(0x1_0000_0001L, request.instanceId());
        assertEquals(0, request.sourceSlot());
        assertEquals(729, request.sourceItemId());
        assertEquals(10011, request.destinationInventoryId());
        assertEquals(1, request.destinationSlot());
        assertEquals(1039, request.destinationObservedItemId());
        assertEquals(
            0x1_0000_0005L,
            codec.decode(
                InventoryFixtures.fixture(
                    "PqMoveInventoryItem-native.root"
                )
            ).instanceId()
        );
        var updates = new InventoryUpdateCodec();

        assertArrayEquals(root("move-reply-two-ids"), updates.moved(new PrMoveInventoryItem(10010, 10011)));

        for (boolean swapped : new boolean[]{false, true}) {
            var bag = new Inventory(
                10010,
                64,
                List.of(
                    item(swapped ? 3 : 1, 0, swapped ? 1039 : 729, 1),
                    item(2, 1, 909, 16)
                )
            );
            var equipment = new Inventory(10011, 16, List.of(item(swapped ? 1 : 3, 1, swapped ? 729 : 1039, 1)));
            String suffix = swapped ? "after-swap" : "before-swap";

            assertArrayEquals(root("bag-" + suffix), updates.update(new PcUpdateInventory(bag)));
            assertArrayEquals(root("equipment-" + suffix), updates.update(new PcUpdateInventory(equipment)));
        }

        for (int length = 0; length < root.length; length++) {
            byte[] truncated = Arrays.copyOf(root, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
        root[0] = 0;
        assertThrows(MalformedPacketException.class, () -> codec.decode(root));
    }

    private byte[] root(String name) throws Exception {
        byte[] frame = InventoryFixtures.fixture(name + ".frame");
        return Arrays.copyOfRange(frame, 4, frame.length);
    }

    private InventoryItemData item(long id, int slot, long definition, int quantity) {
        return new InventoryItemData(0x1_0000_0000L + id, slot, definition, quantity, 288, 288, 288);
    }
}

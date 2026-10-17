package taan.online.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.account.model.AccountGender;

class InventoryModelTest {
    @Test
    void enforcesStableIdentityAndUnsignedItemRanges() {
        for (long id : new long[]{-1, 0, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryId(id));
        }

        assertEquals(0xffff_ffffL, new InventoryId(0xffff_ffffL).value());
        assertEquals(Long.MAX_VALUE, new InventoryItemId(Long.MAX_VALUE).value());
        assertThrows(IllegalArgumentException.class, () -> new InventoryItemId(0));
        assertThrows(IllegalArgumentException.class, () -> new InventoryItemId(-1));

        var id = new InventoryItemId(1);

        for (int slot : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem(id, slot, 1, 1, 0, 0, 0));
        }

        for (long item : new long[]{0, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem(id, 0, item, 1, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new StarterItem(0, item, 0, 0, 0));
        }

        for (int quantity : new int[]{0, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem(id, 0, 1, quantity, 0, 0, 0));
        }

        for (int parameter : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem(id, 0, 1, 1, parameter, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new StarterItem(0, 1, parameter, 0, 0));
        }

        for (int slot : new int[]{-1, 16}) {
            assertThrows(IllegalArgumentException.class, () -> new StarterItem(slot, 1, 0, 0, 0));
        }

        assertEquals(65535, new InventoryItem(id, 255, 0xffff_ffffL, 65535, 65535, 65535, 65535).quantity());
        assertEquals(65535, new StarterItem(15, 0xffff_ffffL, 65535, 65535, 65535).palette3Id());
    }

    @Test
    void keepsInventorySnapshotsImmutableSortedAndFreeOfSlotCollisions() {
        var equipment = SelectionFixtures.inventories(AccountGender.MALE).get(1);
        var values = new ArrayList<>(equipment.items());
        var snapshot = new CharacterInventory(equipment.id(), equipment.kind(), values.reversed());
        values.clear();

        assertEquals(equipment, snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.items().clear());
        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterInventory(
                equipment.id(),
                InventoryKind.EQUIPMENT,
                List.of(equipment.items().getFirst(), equipment.items().getFirst())
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterInventory(
                equipment.id(),
                InventoryKind.EQUIPMENT,
                List.of(new InventoryItem(new InventoryItemId(1), 16, 1, 1, 0, 0, 0))
            )
        );

        var jewel = new CharacterInventory(
            new InventoryId(3),
            InventoryKind.JEWEL_BOX,
            List.of(new InventoryItem(new InventoryItemId(1), 255, 1, 1, 0, 0, 0))
        );

        assertEquals(255, jewel.items().getFirst().slot());
        assertEquals(80, InventoryKind.BAG.capacity());
        assertTrue(new StarterEquipment(List.of()).items().isEmpty());
        assertThrows(
            IllegalArgumentException.class,
            () -> new StarterEquipment(
                List.of(
                    new StarterItem(1, 1, 0, 0, 0),
                    new StarterItem(1, 2, 0, 0, 0)
                )
            )
        );
    }
}

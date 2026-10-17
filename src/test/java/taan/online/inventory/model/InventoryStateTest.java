package taan.online.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.account.model.AccountGender;

class InventoryStateTest {
    @Test
    void freezesDefinitionsAndValidatesResourceGenderSlotsAndPaletteBounds() {
        var catalog = new ItemCatalog();

        assertThrows(NullPointerException.class, () -> catalog.available(729));
        assertThrows(
            IllegalArgumentException.class,
            () -> catalog.initialize(
                List.of(
                    definition(
                        1,
                        1
                    ),
                    definition(
                        1,
                        1
                    )
                )
            )
        );
        catalog.initialize(InventoryFixtures.definitions());

        assertThrows(IllegalStateException.class, () -> catalog.initialize(List.of()));
        assertFalse(catalog.available(0));
        assertFalse(catalog.available(636));
        assertTrue(catalog.available(688));
        assertFalse(catalog.fits(0, 1, AccountGender.MALE));
        assertFalse(catalog.fits(888, 1, AccountGender.MALE));
        assertTrue(catalog.fits(1039, 1, AccountGender.MALE));
        assertFalse(catalog.fits(1039, 2, AccountGender.MALE));
        assertFalse(catalog.fits(1039, 1, AccountGender.FEMALE));
        assertTrue(catalog.fits(806, 1, AccountGender.FEMALE));
        assertFalse(catalog.fits(806, 1, AccountGender.MALE));
        assertTrue(catalog.fits(739, 11, AccountGender.FEMALE));
        assertTrue(catalog.fits(688, 2, AccountGender.MALE));

        for (long id : new long[]{0, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> definition(id, 1));
        }

        for (int slot : new int[]{-1, 16}) {
            assertThrows(IllegalArgumentException.class, () -> definition(1, slot));
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new ItemDefinition(
                1,
                " ",
                null,
                ItemGender.MALE,
                0,
                0,
                0,
                true
            )
        );

        for (int value : new int[]{-1, 65536}) {
            for (int field = 0; field < 3; field++) {
                int[] palettes = {0, 0, 0};
                palettes[field] = value;
                assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemDefinition(
                        1,
                        "Name",
                        null,
                        ItemGender.MALE,
                        palettes[0],
                        palettes[1],
                        palettes[2],
                        true
                    )
                );
            }
        }

        for (var item : List.of(item(1, 0, 0xffff_ffffL, 1), item(1, 1, 1039, 2), item(1, 2, 1039, 1))) {
            assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validate(
                    container(
                        2,
                        InventoryKind.EQUIPMENT,
                        item
                    ),
                    AccountGender.MALE
                )
            );
        }

        catalog.validate(container(1, InventoryKind.BAG, item(1, 79, 806, 5)), AccountGender.MALE);
        catalog.validate(container(2, InventoryKind.EQUIPMENT, item(1, 1, 1039, 1)), AccountGender.MALE);
    }

    @Test
    void enforcesDistinctContainerKindsIdsAndInstanceIds() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryState(List.of()));
        var bag = container(1, InventoryKind.BAG);
        var equipment = container(2, InventoryKind.EQUIPMENT);
        var jewels = container(3, InventoryKind.JEWEL_BOX);

        assertThrows(IllegalArgumentException.class, () -> new InventoryState(List.of(bag, bag, jewels)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new InventoryState(
                List.of(
                    bag,
                    equipment,
                    container(
                        1,
                        InventoryKind.JEWEL_BOX
                    )
                )
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> state(
                List.of(item(1, 0, 1039, 1)),
                List.of(item(1, 1, 1039, 1))
            )
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> new InventoryState(
                List.of(
                    bag,
                    equipment,
                    jewels
                )
            ).inventories().clear()
        );
    }

    @Test
    void checksBothEndsOfTransfersAndSwapsAndPreservesInstanceIdentity() {
        var catalog = InventoryFixtures.catalog();
        var state = state(List.of(item(0x1_0000_0001L, 79, 1039, 1)), List.of(item(2, 1, 729, 1)));
        var moved = state.move(move(1, 0x1_0000_0001L, 79, 1039, 2, 1, 729), catalog, AccountGender.MALE);

        assertEquals(InventoryMoveOutcome.MOVED, moved.outcome());
        assertEquals(
            List.of(
                new ItemPlacement(new InventoryItemId(0x1_0000_0001L), new InventoryId(2), 1),
                new ItemPlacement(new InventoryItemId(2), new InventoryId(1), 79)
            ),
            moved.placements()
        );
        assertThrows(UnsupportedOperationException.class, () -> moved.placements().clear());
        assertEquals(
            InventoryMoveOutcome.MOVED,
            state.move(
                move(
                    2,
                    2,
                    1,
                    729,
                    1,
                    0,
                    0
                ),
                catalog,
                AccountGender.MALE
            ).outcome()
        );
        assertEquals(
            InventoryMoveOutcome.MOVED,
            state.move(
                move(
                    1,
                    0x1_0000_0001L,
                    79,
                    1039,
                    1,
                    0,
                    0
                ),
                catalog,
                AccountGender.MALE
            ).outcome()
        );
        assertEquals(
            InventoryMoveOutcome.NO_CHANGE,
            state.move(
                move(
                    1,
                    0x1_0000_0001L,
                    79,
                    1039,
                    1,
                    79,
                    1039
                ),
                catalog,
                AccountGender.MALE
            ).outcome()
        );

        var badSwap = state(List.of(item(1, 79, 578, 1)), List.of(item(2, 1, 729, 1)));

        assertEquals(
            InventoryMoveOutcome.INCOMPATIBLE_EQUIPMENT,
            badSwap.move(
                move(
                    2,
                    2,
                    1,
                    729,
                    1,
                    79,
                    578
                ),
                catalog,
                AccountGender.MALE
            ).outcome()
        );
        assertEquals(
            InventoryMoveOutcome.INCOMPATIBLE_EQUIPMENT,
            state.move(
                move(
                    1,
                    0x1_0000_0001L,
                    79,
                    1039,
                    2,
                    1,
                    729
                ),
                catalog,
                AccountGender.FEMALE
            ).outcome()
        );

        var unsupported = state(List.of(item(1, 0, 636, 1)), List.of());

        assertEquals(
            InventoryMoveOutcome.INCOMPATIBLE_EQUIPMENT,
            unsupported.move(
                move(
                    1,
                    1,
                    0,
                    636,
                    1,
                    1,
                    0
                ),
                catalog,
                AccountGender.MALE
            ).outcome()
        );
    }

    @Test
    void rejectsUnknownContainersBadSlotsStaleTuplesAndStacksWithoutPlacements() {
        var state = state(List.of(item(1, 0, 1039, 1), item(3, 1, 578, 2)), List.of(item(2, 1, 729, 1)));
        var cases = List.of(
            move(99, 1, 0, 1039, 2, 1, 729),
            move(1, 1, 0, 1039, 99, 1, 729),
            move(3, 1, 0, 1039, 2, 1, 729),
            move(1, 1, 0, 1039, 3, 1, 729),
            move(1, 1, -1, 1039, 2, 1, 729),
            move(1, 1, 80, 1039, 2, 1, 729),
            move(1, 1, 0, 1039, 2, -1, 729),
            move(1, 1, 0, 1039, 2, 16, 729),
            move(1, 1, 2, 1039, 2, 1, 729),
            move(1, 9, 0, 1039, 2, 1, 729),
            move(1, 1, 0, 578, 2, 1, 729),
            move(1, 1, 0, 1039, 2, 1, 578),
            move(1, 1, 0, 1039, 1, 2, 578),
            move(1, 3, 1, 578, 1, 2, 0),
            move(1, 1, 0, 1039, 1, 1, 578)
        );
        var outcomes = List.of(
            InventoryMoveOutcome.UNKNOWN_CONTAINER,
            InventoryMoveOutcome.UNKNOWN_CONTAINER,
            InventoryMoveOutcome.UNSUPPORTED_CONTAINER,
            InventoryMoveOutcome.UNSUPPORTED_CONTAINER,
            InventoryMoveOutcome.INVALID_SLOT,
            InventoryMoveOutcome.INVALID_SLOT,
            InventoryMoveOutcome.INVALID_SLOT,
            InventoryMoveOutcome.INVALID_SLOT,
            InventoryMoveOutcome.STALE_SOURCE,
            InventoryMoveOutcome.STALE_SOURCE,
            InventoryMoveOutcome.STALE_SOURCE,
            InventoryMoveOutcome.STALE_DESTINATION,
            InventoryMoveOutcome.STALE_DESTINATION,
            InventoryMoveOutcome.UNSUPPORTED_QUANTITY,
            InventoryMoveOutcome.UNSUPPORTED_QUANTITY
        );

        for (int index = 0; index < cases.size(); index++) {
            var result = state.move(cases.get(index), InventoryFixtures.catalog(), AccountGender.MALE);

            assertEquals(outcomes.get(index), result.outcome());
            assertTrue(result.placements().isEmpty());
        }
    }

    static InventoryState state(List<InventoryItem> bag, List<InventoryItem> equipment) {
        return new InventoryState(
            List.of(
                container(1, InventoryKind.BAG, bag.toArray(InventoryItem[]::new)),
                container(
                    2,
                    InventoryKind.EQUIPMENT,
                    equipment.toArray(
                        InventoryItem[]::new
                    )
                ),
                container(
                    3,
                    InventoryKind.JEWEL_BOX
                )
            )
        );
    }

    static CharacterInventory container(long id, InventoryKind kind, InventoryItem... items) {
        return new CharacterInventory(new InventoryId(id), kind, List.of(items));
    }

    static InventoryItem item(long id, int slot, long definition, int quantity) {
        return new InventoryItem(new InventoryItemId(id), slot, definition, quantity, 303, 0, 0);
    }

    static InventoryMove move(long from, long uid, int slot, long item, long to, int targetSlot, long observed) {
        return new InventoryMove(from, uid, slot, item, to, targetSlot, observed);
    }

    private ItemDefinition definition(long id, Integer slot) {
        return new ItemDefinition(id, "Item", slot, ItemGender.MALE, 0, 0, 0, true);
    }
}

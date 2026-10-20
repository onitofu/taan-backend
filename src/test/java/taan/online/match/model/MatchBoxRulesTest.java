package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class MatchBoxRulesTest {
    @Test
    void validatesNativeIdsAndMapBoundsWithoutAssigningRewardsOrRequiringGroundSupport() {
        var map = MatchFixtures.definitions().map();
        long id = 0x05ff000000ffffffL;
        var box = MatchBoxRules.create(Map.of(), map, 1, 0, id, 0, 0);
        assertEquals(new MatchItemBox(id, 0, 0, true), box);
        assertEquals(35, MatchItemBox.RADIUS);
        assertEquals(1225, MatchItemBox.RADIUS_SQUARED);
        var edge = MatchBoxRules.create(Map.of(), map, 1, 0, 0x0500000000000000L, map.width() - 1, map.height() - 1);
        assertEquals(map.width() - 1, edge.x());
        assertEquals(map.height() - 1, edge.y());

        for (long invalid : new long[]{-1, 0, 0x0400000000000001L, 0x0500000001000001L}) {
            assertEquals("INVALID_BOX_ID", assertThrows(
                ScenarioWorldException.class,
                () -> MatchBoxRules.create(Map.of(), map, 1, 0, invalid, 0, 0)
            ).getMessage());
        }

        for (int[] point : new int[][]{{-1, 0}, {map.width(), 0}, {0, -1}, {0, map.height()}}) {
            assertEquals("INVALID_BOX_POSITION", assertThrows(
                ScenarioWorldException.class,
                () -> MatchBoxRules.create(Map.of(), map, 1, 0, id, point[0], point[1])
            ).getMessage());
        }
    }

    @Test
    void rejectsReusedInactiveIdsAndCountsOnlyActiveBoxesForCapacityAndNextTurnBudget() {
        var map = MatchFixtures.definitions().map();
        var boxes = new HashMap<Long, MatchItemBox>();
        assertEquals(0, MatchBoxRules.budget(MatchBoxPolicy.DISABLED, boxes));
        assertEquals(1, MatchBoxRules.budget(MatchBoxPolicy.ENABLED, boxes));

        for (int i = 1; i <= 5; i++) {
            long id = 0x0500000000000000L | i;
            boxes.put(id, MatchBoxRules.create(boxes, map, 1, 0, id, i, i));
        }

        assertEquals(5, MatchBoxRules.activeCount(boxes));
        assertEquals(0, MatchBoxRules.budget(MatchBoxPolicy.ENABLED, boxes));
        assertEquals("ACTIVE_BOX_LIMIT_EXCEEDED", assertThrows(
            ScenarioWorldException.class,
            () -> MatchBoxRules.create(boxes, map, 1, 0, 0x0500000000000006L, 0, 0)
        ).getMessage());
        var first = boxes.get(0x0500000000000001L);
        boxes.put(first.entityId(), new MatchItemBox(first.entityId(), first.x(), first.y(), false));
        assertEquals(4, MatchBoxRules.activeCount(boxes));
        assertEquals(1, MatchBoxRules.budget(MatchBoxPolicy.ENABLED, boxes));
        assertEquals("DUPLICATE_BOX_ID", assertThrows(
            ScenarioWorldException.class,
            () -> MatchBoxRules.create(boxes, map, 1, 0, first.entityId(), 0, 0)
        ).getMessage());

        for (int[] limits : new int[][]{{0, 0}, {1, 1}, {-1, 0}, {2, 0}}) {
            assertEquals("BOX_BUDGET_EXCEEDED", assertThrows(
                ScenarioWorldException.class,
                () -> MatchBoxRules.create(Map.of(), map, limits[0], limits[1], 0x0500000000000006L, 0, 0)
            ).getMessage());
        }
    }

    @Test
    void boundsTheRetainedHistoryAndBindsRewardsOnlyWhenAnActiveBoxIsPickedUp() {
        var boxes = new HashMap<Long, MatchItemBox>();

        for (int index = 1; index < MatchBoxRules.MAX_CREATED; index++) {
            long id = 0x0500000000000000L | index;
            boxes.put(id, new MatchItemBox(id, 0, 0, false));
        }

        assertEquals(1, MatchBoxRules.budget(MatchBoxPolicy.ENABLED, boxes));
        long id = 0x0500000000000000L | MatchBoxRules.MAX_CREATED;
        var last = MatchBoxRules.create(boxes, MatchFixtures.definitions().map(), 1, 0, id, 0, 0);
        boxes.put(id, last.pickedUp(5));
        assertEquals(0, MatchBoxRules.activeCount(boxes));
        assertEquals(0, MatchBoxRules.budget(MatchBoxPolicy.ENABLED, boxes));
        assertEquals(5, boxes.get(id).boundItemId().orElseThrow());
        assertEquals("BOX_HISTORY_LIMIT_EXCEEDED", assertThrows(
            ScenarioWorldException.class,
            () -> MatchBoxRules.create(boxes, MatchFixtures.definitions().map(), 1, 0, id + 1, 0, 0)
        ).getMessage());
        assertThrows(IllegalArgumentException.class, () -> new MatchItemBox(id + 1, 0, 0, true, OptionalInt.of(5)));
        assertEquals("INACTIVE_BOX", assertThrows(
            ScenarioWorldException.class, () -> boxes.get(id).pickedUp(7)
        ).getMessage());
    }

}

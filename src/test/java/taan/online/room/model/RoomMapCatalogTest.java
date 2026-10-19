package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomMapCatalogTest {
    @Test
    void validatesEveryKindAndCopiesBothTheOuterMapAndNestedSets() {
        var ids = new HashSet<>(Set.of(1, 3));
        var beginner = new HashSet<>(Set.of(1));
        var byKind = new HashMap<>(Map.of(0, beginner, 1, Set.of(1), 4, Set.of(3), 6, ids));
        var catalog = new RoomMapCatalog(ids, byKind);
        ids.clear();
        beginner.clear();
        byKind.clear();

        assertEquals(Set.of(1, 3), catalog.mapIds());
        assertEquals(Set.of(1), catalog.forKind(0).mapIds());
        assertThrows(UnsupportedOperationException.class, () -> catalog.byKind().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.byKind().get(0).clear());
        assertThrows(IllegalArgumentException.class, () -> new RoomMapCatalog(Set.of(1), Map.of()));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomMapCatalog(Set.of(1), Map.of(0, Set.of(), 1, Set.of(1), 4, Set.of(1), 6, Set.of(1)))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomMapCatalog(Set.of(1), Map.of(0, Set.of(2), 1, Set.of(1), 4, Set.of(1), 6, Set.of(1)))
        );
    }
}

package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class MatchDefinitionCatalogTest {
    @Test
    void rejectsMissingMapsAndIdentityMismatchesAndCopiesReferenceData() {
        var definitions = MatchFixtures.definitions();
        var maps = new HashMap<>(Map.of(1, definitions.map()));
        var catalog = new MatchDefinitionCatalog(
            definitions.catalogVersion(),
            maps,
            definitions.weapons(),
            MatchFixtures.cannonShot()
        );
        maps.clear();

        assertEquals(definitions, catalog.select(1));
        assertThrows(UnsupportedOperationException.class, () -> catalog.maps().clear());
        assertThrows(NullPointerException.class, () -> catalog.select(200));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitionCatalog(
                definitions.catalogVersion(),
                Map.of(),
                definitions.weapons(),
                MatchFixtures.cannonShot()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitionCatalog(
                definitions.catalogVersion(),
                Map.of(2, definitions.map()),
                definitions.weapons(),
                MatchFixtures.cannonShot()
            )
        );
    }

    @Test
    void acceptsOnlyConfirmedOrdinaryChannelKindsAndCopiesTheirSet() {
        var map = MatchFixtures.definitions().map();
        var kinds = new HashSet<>(Set.of(0, 1, 4, 6));
        var copy = new MatchMapDefinition(map.mapId(), map.asset(), map.width(), map.height(), map.spawns(), kinds);
        kinds.clear();

        assertEquals(Set.of(0, 1, 4, 6), copy.channelKinds());
        assertThrows(UnsupportedOperationException.class, () -> copy.channelKinds().clear());

        for (var invalid : Set.of(Set.<Integer>of(), Set.of(2), Set.of(7))) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchMapDefinition(map.mapId(), map.asset(), map.width(), map.height(), map.spawns(), invalid)
            );
        }
    }
}

package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.service.ScenarioTestWorld;

class TerrainDefinitionInvariantTest {
    @Test
    void requiresBoundTerrainAndConsistentMissileIdentities() {
        var d = MatchFixtures.definitions();
        var m = d.map();
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchMapDefinition(
                m.mapId(),
                m.asset(),
                m.width(),
                m.height(),
                m.spawns(),
                m.channelKinds(),
                List.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(d.catalogVersion(), m, d.weapons(), d.cannonShots(), Map.of())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(
                d.catalogVersion(),
                m,
                d.weapons(),
                d.cannonShots(),
                Map.of(0, d.missiles().get(0), 1, d.missiles().get(0))
            )
        );
    }

    @Test
    void requiresBothAttacksAndMatchingAttackAndMissileKeys() {
        var d = MatchFixtures.definitions();
        for (var shots : List.of(
            Map.of(0, d.cannonShots().get(0)),
            Map.of(0, d.cannonShots().get(2), 2, d.cannonShots().get(0))
        )) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchDefinitions(d.catalogVersion(), d.map(), d.weapons(), shots, d.missiles())
            );
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(
                d.catalogVersion(),
                d.map(),
                d.weapons(),
                d.cannonShots(),
                Map.of(0, d.missiles().get(0), 2, d.missiles().get(2), 1, d.missiles().get(0))
            )
        );
    }

    @Test
    void refusesActorsWhichExistInTheWorldButHaveNoRosterIdentity() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var id = UUID.randomUUID();
            var state = MatchWorldState.initial(
                Map.of(id, new MatchActorState(1, 1, 0, 1000, 0)),
                f.definitions.map().terrainLayers()
            );
            var draft = new ScenarioWorldDraft(state, f.definitions, f.members);
            assertThrows(ScenarioWorldException.class, () -> draft.dead(id));
        }
    }
}

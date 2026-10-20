package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.service.PrepareScenarioAction;
import taan.online.match.service.ScenarioTestWorld;

class ScenarioWorldDraftTest {
    @Test
    void appliesNativeDamageDropFallAndEnergyWithoutUsingSimulationHp() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var before = f.snapshot();
            var accepted = f.execute(f.claims).accepted().orElseThrow();
            var prepared = new PrepareScenarioAction().execute(before, accepted);
            var after = prepared.world();
            var session = f.sender.sessionId();

            assertEquals(0, after.actors().get(session).hp());
            assertEquals(80, after.actors().get(session).energy());
            assertEquals(1150, after.actors().get(session).y());
            assertEquals(1000, before.actors().get(session).hp());
            assertEquals(650, before.actors().get(session).y());
            assertEquals(1, after.revision());
            assertEquals(before.world().revision(), prepared.baseWorldRevision());
            assertEquals(accepted, prepared.scenario());
            assertEquals(1, prepared.deaths());
            assertTrue(prepared.changedCells() >= 0);
            assertEquals(accepted.hash(), after.receipt().orElseThrow().fingerprint());
            var fall = after.falls().get(session).getFirst();

            assertEquals(2, fall.slot());
            assertEquals(1193, fall.tick());
            assertEquals(651, fall.duration());
            assertEquals(933, fall.reportedStartY());
            assertEquals(650, fall.effectiveStartY());
            assertEquals(1150, fall.endY());
            assertThrows(UnsupportedOperationException.class, () -> after.actors().clear());
            assertThrows(UnsupportedOperationException.class, () -> after.falls().get(session).clear());
            assertThrows(UnsupportedOperationException.class, () -> after.processedDeaths().clear());
        }
    }

    @Test
    void subtractsDamageWithLongArithmeticAndDeduplicatesExplicitDeaths() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var before = f.snapshot().world();
            var id = f.sender.sessionId();
            var draft = new ScenarioWorldDraft(before, f.definitions, f.members);
            draft.damage(id, 10);
            draft.damage(id, 15);
            var result = draft.finish(receipt(f, 1), Set.of(), 1000);

            assertEquals(975, result.actors().get(id).hp());
            assertTrue(result.processedDeaths().isEmpty());
            draft.damage(id, Integer.MAX_VALUE);
            draft.dead(id);
            draft.dead(id);
            result = draft.finish(receipt(f, 1), Set.of(), 1000);

            assertEquals(0, result.actors().get(id).hp());
            assertEquals(Set.of(id), result.processedDeaths());
            assertEquals(1000, before.actors().get(id).hp());
            assertThrows(ScenarioWorldException.class, () -> draft.damage(id, -1));
            assertThrows(ScenarioWorldException.class, () -> draft.dead(UUID.randomUUID()));
            assertThrows(ScenarioWorldException.class, () -> draft.finish(receipt(f, 9), Set.of(), 1000));
            assertThrows(
                ScenarioWorldException.class,
                () -> draft.finish(receipt(f, 1), Set.of(UUID.randomUUID()), 1000)
            );
        }
    }

    @Test
    void keepsPrivateTerrainChangesAndPreservesOtherCellFlagsAndOverlappingLayers() {
        byte[] cells = {0, 1, 2, 17, 16, 1, 1, 1};
        var layer = new TerrainLayerDefinition(10, 20, 4, 2, cells);
        var mask = new TerrainLayerDefinition(0, 0, 4, 2, new byte[]{1, 1, 1, 1, 0, 1, 1, 1});
        var missile = new MissileTerrainDefinition(0, 0, 0, 0, mask);
        var draft = new ScenarioTerrainDraft(List.of(layer, layer));
        cells[1] = 0;
        draft.explode(missile, 10, 20, 1);
        var after = draft.freeze();

        assertEquals(8, draft.changedCells());
        assertArrayEquals(new byte[]{0, 17, 2, 17, 16, 17, 17, 17}, after.getFirst().cells());
        assertEquals(1, layer.value(11, 20));
        byte[] exposed = after.getFirst().cells();
        exposed[1] = 1;
        assertEquals(17, after.getFirst().value(11, 20));
        assertEquals(0, layer.value(9, 20));
        assertEquals(0, layer.value(14, 20));
        assertEquals(0, layer.value(10, 19));
        assertEquals(0, layer.value(10, 22));
        draft.explode(missile, 10, 20, 1);
        assertEquals(8, draft.changedCells());
        assertEquals(1, layer.value(11, 20));
        assertEquals(10, layer.left());
        assertEquals(20, layer.top());
        assertEquals(4, layer.width());
        assertEquals(2, layer.height());
    }

    @Test
    void clipsMasksHandlesFractionalAndZeroFactorsAndRejectsOverflowWithoutPublishing() {
        byte[] cells = new byte[64];
        Arrays.fill(cells, (byte) 1);
        var layer = new TerrainLayerDefinition(0, 0, 8, 8, cells);
        var mask = new TerrainLayerDefinition(0, 0, 4, 4, new byte[]{1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1});
        var missile = new MissileTerrainDefinition(0, 0, 2, 2, mask);
        var draft = new ScenarioTerrainDraft(List.of(layer));
        draft.explode(missile, 0, 0, 0);
        draft.explode(missile, 0, 0, Float.MIN_VALUE);
        draft.explode(missile, 999, 999, 1);

        assertEquals(0, draft.changedCells());
        assertSame(layer, draft.freeze().getFirst());
        draft.explode(missile, 0, 0, .5f);
        assertEquals(1, draft.changedCells());
        assertArrayEquals(cells, layer.cells());

        for (float invalid : new float[]{Float.NaN, -.1f, 1.1f}) {
            assertThrows(ScenarioWorldException.class, () -> draft.explode(missile, 0, 0, invalid));
        }

        for (int x : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(ScenarioWorldException.class, () -> draft.explode(missile, x, 0, 1));
        }

        assertThrows(ScenarioWorldException.class, () -> draft.explode(missile, 0, Integer.MAX_VALUE, 1));
        assertThrows(ScenarioWorldException.class, () -> draft.explode(missile, 0, Integer.MIN_VALUE, 1));
    }

    @Test
    void normalizesOverlappingFallsAndDiscardsOnlyTheShorterOverlappingReport() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var id = f.sender.sessionId();
            var draft = new ScenarioWorldDraft(f.snapshot().world(), f.definitions, f.members);
            draft.fall(id, 100, 100, 1, 750);
            draft.fall(id, 150, 100, 1, 700);
            draft.fall(id, 150, 100, 1, 850);
            draft.fall(id, 254, 100, 1, 900);
            var partial = draft.finish(receipt(f, 1), Set.of(), 200);

            assertEquals(3, partial.falls().get(id).size());
            assertEquals(700, partial.falls().get(id).get(1).effectiveStartY());
            assertTrue(partial.actors().get(id).y() >= 750);
            var finalState = draft.finish(receipt(f, 1), Set.of(), 1000);
            assertTrue(finalState.actors().get(id).y() >= 850);
            draft.fall(id, 50, 10, 1, 660);
            assertEquals(4, draft.finish(receipt(f, 1), Set.of(), 1000).falls().get(id).size());
            assertThrows(ScenarioWorldException.class, () -> draft.explode(new WeaponMissileKey(0, 65535), 1, 1, 1));

            for (long[] invalid : new long[][]{
                {-1, 1, 0, 1}, {120001, 1, 0, 1}, {0, 0, 0, 1}, {120000, 1, 0, 1},
                {0, 1, -1, 1}, {0, 1, 10, 9}, {0, 1, 0, 1151}
            }) {
                assertThrows(
                    ScenarioWorldException.class,
                    () -> draft.fall(id, invalid[0], (int) invalid[1], (int) invalid[2], (int) invalid[3])
                );
            }
        }
    }

    @Test
    void rejectsInvalidDefinitionsAndKeepsSnapshotsDetachedFromInputCollections() {
        for (int[] row : new int[][]{{0, 1}, {1, 0}, {2, 2}}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainLayerDefinition(0, 0, row[0], row[1], new byte[1])
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new TerrainLayerDefinition(Integer.MAX_VALUE, 0, 1, 1, new byte[1])
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new TerrainLayerDefinition(0, Integer.MAX_VALUE, 1, 1, new byte[1])
        );
        var mask = new TerrainLayerDefinition(0, 0, 2, 2, new byte[4]);

        for (int[] row : new int[][]{{-1, 0, 0}, {65536, 0, 0}, {0, -1, 0}, {0, 2, 0}, {0, 0, -1}, {0, 0, 2}}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MissileTerrainDefinition(0, row[0], row[1], row[2], mask)
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new MissileTerrainDefinition(0, 0, 0, 0, new TerrainLayerDefinition(1, 0, 1, 1, new byte[1]))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MissileTerrainDefinition(0, 0, 0, 0, new TerrainLayerDefinition(0, 1, 1, 1, new byte[1]))
        );
        var layers = new ArrayList<>(List.of(mask));
        var initial = MatchWorldState.initial(Map.of(), layers);
        layers.clear();
        assertEquals(1, initial.terrain().size());
        assertFalse(initial.receipt().isPresent());
    }

    private ScenarioCommitReceipt receipt(ScenarioTestWorld f, long revision) {
        return new ScenarioCommitReceipt("a".repeat(64), f.sender.sessionId(), f.sender.admissionId(), 7, revision);
    }
}

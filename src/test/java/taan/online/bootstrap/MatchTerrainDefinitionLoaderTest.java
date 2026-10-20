package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class MatchTerrainDefinitionLoaderTest {
    private static final String LAYERS = "map_id,layer_index,left,top,width,height,resource,sha256\n";
    private static final String MISSILES = "missile_type_id,width,height,center_x,center_y,resource,sha256\n";
    private final MatchTerrainDefinitionLoader loader = new MatchTerrainDefinitionLoader();

    @Test
    void loadsAllOrdinaryLayersAndTheCanonicalCannonMaskWithExportedChecksums() throws Exception {
        var catalog = MatchFixtures.catalog();
        var expected = IntStream.rangeClosed(1, 21).boxed().collect(Collectors.toSet());
        assertEquals(expected, catalog.maps().keySet());
        assertEquals(143, catalog.maps().values().stream().mapToInt(map -> map.terrainLayers().size()).sum());
        var cannon = catalog.missiles().get(0);
        assertEquals(0, cannon.missileTypeId());
        assertEquals(256, cannon.mask().width());
        assertEquals(256, cannon.mask().height());
        assertEquals(128, cannon.centerX());
        assertEquals(150, cannon.centerY());
        assertTrue(cannon.mask().cells().length > 0);
        String provenance = text("terrain-provenance.json");

        for (var map : catalog.maps().values()) {
            for (var layer : map.terrainLayers()) {
                assertTrue(provenance.contains(hash(layer.cells())));
            }
        }

        assertEquals(Set.of(0, 1, 2), catalog.missiles().keySet());
        assertTrue(provenance.contains(hash(cannon.mask().cells())));
    }

    @Test
    void rejectsMalformedIndexesDimensionsResourceNamesRowsValuesAndHashes() throws Exception {
        String valid = "TAAN_RLE_V1,2,1\n2:1\n";
        String row = "1,0,0,0,2,1,terrain/test.rle," + hash(new byte[]{1, 1}) + "\n";
        assertEquals(1, loader.layers(input(LAYERS + row), ignored -> input(valid)).size());

        for (String invalid : new String[]{
            "", LAYERS + "x\n", LAYERS + row + row,
            LAYERS + row.replace("1,0,", "0,0,"),
            LAYERS + row.replace("1,0,", "1,2,"),
            LAYERS + row.replace("2,1,terrain", "0,1,terrain"),
            LAYERS + row.replace("2,1,terrain", "2,0,terrain"),
            LAYERS + row.replace("2,1,terrain", "100000,100000,terrain"),
            LAYERS + row.replace("terrain/test.rle", "../test.rle"),
            LAYERS + row.replace(hash(new byte[]{1, 1}), "bad")
        }) {
            assertThrows(IllegalArgumentException.class, () -> loader.layers(input(invalid), ignored -> input(valid)));
        }

        for (String invalid : new String[]{
            "", "TAAN_RLE_V1,1,1\n2:1\n", "TAAN_RLE_V1,2,1\n",
            "TAAN_RLE_V1,2,1\n2\n", "TAAN_RLE_V1,2,1\n0:1\n", "TAAN_RLE_V1,2,1\n3:1\n",
            "TAAN_RLE_V1,2,1\n2:-1\n", "TAAN_RLE_V1,2,1\n2:256\n", "TAAN_RLE_V1,2,1\n1:1\n",
            "TAAN_RLE_V1,2,1\n2:1\n2:1\n"
        }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> loader.layers(input(LAYERS + row), ignored -> input(invalid))
            );
        }

        assertThrows(NullPointerException.class, () -> loader.layers(null, ignored -> input(valid)));
        assertThrows(NullPointerException.class, () -> loader.layers(input(LAYERS + row), ignored -> null));
    }

    @Test
    void validatesMaskIdentityValuesAndClosesResourcesOnSuccessAndFailure() throws Exception {
        String row = "0,2,1,0,0,terrain/test.rle," + hash(new byte[]{1, 1}) + "\n";
        var index = new TrackingDefinitionStream((MISSILES + row + row.replaceFirst("0,", "1,")
            + row.replaceFirst("0,", "2,")).getBytes(StandardCharsets.UTF_8));
        var mask = new TrackingDefinitionStream("TAAN_RLE_V1,2,1\n2:1\n".getBytes(StandardCharsets.UTF_8));
        var secondMask = new TrackingDefinitionStream("TAAN_RLE_V1,2,1\n2:1\n".getBytes(StandardCharsets.UTF_8));
        var thirdMask = new TrackingDefinitionStream("TAAN_RLE_V1,2,1\n2:1\n".getBytes(StandardCharsets.UTF_8));
        var masks = List.of(mask, secondMask, thirdMask);
        var reads = new AtomicInteger();

        assertEquals(3, loader.missiles(index, ignored -> masks.get(reads.getAndIncrement())).size());
        assertTrue(index.closed());
        assertTrue(mask.closed());
        assertTrue(secondMask.closed());
        assertTrue(thirdMask.closed());

        for (String invalid : new String[]{MISSILES, MISSILES + row + row, MISSILES + row.replace("0,2,", "1,2,")}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> loader.missiles(input(invalid), ignored -> input("TAAN_RLE_V1,2,1\n2:1\n"))
            );
        }

        var invalidCell = MISSILES + row.replace(hash(new byte[]{1, 1}), hash(new byte[]{0, 2}));
        assertThrows(
            IllegalArgumentException.class,
            () -> loader.missiles(input(invalidCell), ignored -> input("TAAN_RLE_V1,2,1\n1:0 1:2\n"))
        );
        var zeroRow = row.replace(hash(new byte[]{1, 1}), hash(new byte[]{0, 1}));
        var zeroOne = MISSILES + zeroRow + zeroRow.replaceFirst("0,", "1,") + zeroRow.replaceFirst("0,", "2,");
        assertEquals(3, loader.missiles(input(zeroOne), ignored -> input("TAAN_RLE_V1,2,1\n1:0 1:1\n")).size());
        assertThrows(
            IllegalArgumentException.class,
            () -> loader.missiles(input(MISSILES + row.replace("0,2,1,", "0,100000,100000,")), ignored -> null)
        );

        String completeIndex = MISSILES + row + row.replaceFirst("0,", "1,") + row.replaceFirst("0,", "2,");

        for (boolean read : new boolean[]{true, false}) {
            assertThrows(
                IllegalStateException.class,
                () -> loader.missiles(
                    new BrokenDefinitionStream(
                        completeIndex.getBytes(StandardCharsets.UTF_8),
                        read
                    ),
                    ignored -> input("TAAN_RLE_V1,2,1\n2:1\n")
                )
            );
            assertThrows(
                IllegalStateException.class,
                () -> loader.layers(
                    new BrokenDefinitionStream(LAYERS.getBytes(StandardCharsets.UTF_8), read),
                    ignored -> null
                )
            );
        }

        assertThrows(IllegalStateException.class, () -> loader.layers(input(new byte[]{(byte) 255}), ignored -> null));
    }

    @Test
    void rejectsMissingOrOrphanLayersThroughTheCompositionLoader() throws Exception {
        var loader = new MatchDefinitionLoader();
        String source = text("terrain-layers.csv");

        for (String changed : new String[]{source.lines().findFirst().orElseThrow() + "\n", source.replace(
            "21,0,",
            "22,0,"
        ), source + source.lines().skip(1).findFirst().orElseThrow().replaceFirst("1,0,", "22,0,") + "\n"}) {
            Function<String, InputStream> resources = name ->
                name.equals("terrain-layers.csv") ? input(changed) : resource(name);
            assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(
                    resource("catalog.properties"),
                    resource("maps.csv"),
                    resource("map-spawns.csv"),
                    resource("weapons.csv"),
                    resource("cannon-shots.csv"),
                    resources
                )
            );
        }
    }

    @Test
    void reportsAnUnavailableRequiredDigestProviderAsAnInfrastructureFailure() throws Exception {
        String row = "1,0,0,0,2,1,terrain/test.rle," + hash(new byte[]{1, 1}) + "\n";
        var providers = Security.getProviders();

        try {
            for (var provider : providers) {
                Security.removeProvider(provider.getName());
            }

            assertThrows(
                IllegalStateException.class,
                () -> loader.layers(input(LAYERS + row), ignored -> input("TAAN_RLE_V1,2,1\n2:1\n"))
            );
        } finally {
            for (var provider : providers) {
                Security.addProvider(provider);
            }
        }
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String text(String name) throws Exception {
        try (var input = resource(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/match/" + name);
    }

    private ByteArrayInputStream input(String value) {
        return input(value.getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream input(byte[] value) {
        return new ByteArrayInputStream(value);
    }
}

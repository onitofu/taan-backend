package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FixtureManifestTest {
    @Test
    void verifiesEveryUsedFixtureAndRuntimeCatalogAgainstItsRecordedChecksum() throws Exception {
        for (String resource : FixtureManifest.resources()) {
            byte[] bytes;

            try (var input = getClass().getResourceAsStream("/" + resource)) {
                assertNotNull(input, resource);
                bytes = input.readAllBytes();
            }

            if (resource.endsWith(".hex")) {
                bytes = HexFormat.of().parseHex(new String(bytes, StandardCharsets.US_ASCII).replaceAll("\\s+", ""));
            } else if (resource.endsWith(".rle")) {
                var decoded = new ByteArrayOutputStream();

                for (String row : new String(bytes, StandardCharsets.US_ASCII).lines().skip(1).toList()) {
                    for (String run : row.split(" ")) {
                        String[] pair = run.split(":");

                        for (int count = Integer.parseInt(pair[0]); count > 0; count--) {
                            decoded.write(Integer.parseInt(pair[1]));
                        }
                    }
                }

                bytes = decoded.toByteArray();
            }

            FixtureManifest.verify(resource, bytes);
        }
    }

    @Test
    void keepsOnlyRuntimeInputsAndManifestedTestFixtures() throws Exception {
        var expected = new TreeSet<>(
            Set.of(
                "build-info.properties",
                "logback.xml",
                "character/creation-options.csv",
                "character/levels.csv",
                "character/name-filters.txt",
                "room/bootstrap.properties",
                "match/maps.csv",
                "match/map-spawns.csv",
                "match/weapons.csv",
                "match/weapon-attacks.csv",
                "match/weapon-fires.csv",
                "match/terrain-layers.csv",
                "match/missile-terrain.csv",
                "db/migration/V1__create_accounts.sql",
                "db/migration/V2__create_item_catalog.sql",
                "db/migration/V3__create_characters_and_inventories.sql",
                "db/migration/V4__create_channel_catalog.sql",
                "db/migration/V5__create_match_results.sql"
            )
        );
        Path runtime = Path.of("src/main/resources");

        for (String index : Set.of("terrain-layers.csv", "missile-terrain.csv")) {
            var rows = Files.readAllLines(runtime.resolve("match/" + index));

            for (String row : rows.subList(1, rows.size())) {
                expected.add("match/" + row.split(",")[6]);
            }
        }

        try (var paths = Files.walk(runtime)) {
            assertEquals(expected, paths.filter(Files::isRegularFile)
                .map(runtime::relativize).map(Path::toString).collect(TreeSet::new, TreeSet::add, TreeSet::addAll));
        }

        Path fixtures = Path.of("src/test/resources");

        try (var paths = Files.walk(fixtures)) {
            var actual = paths.filter(Files::isRegularFile).map(fixtures::relativize).map(Path::toString)
                .filter(name -> !name.equals("fixture-manifest.tsv"))
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
            var recorded = new TreeSet<>(FixtureManifest.resources());
            recorded.removeAll(expected);
            assertEquals(actual, recorded);
        }
    }
}

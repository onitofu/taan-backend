package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FixtureManifest {
    private static final Map<String, String> HASHES = load();

    private FixtureManifest() {
    }

    public static Set<String> resources() {
        return HASHES.keySet();
    }

    public static void verify(String resource, byte[] canonicalBytes) throws NoSuchAlgorithmException {
        String expected = HASHES.get(resource);
        assertNotNull(expected, resource);
        assertEquals(
            expected,
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes)),
            resource
        );
    }

    private static Map<String, String> load() {
        try (var input = Objects.requireNonNull(FixtureManifest.class.getResourceAsStream("/fixture-manifest.tsv"));
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            assertEquals("resource\toriginal_file\tsha256\torigin", reader.readLine());
            var result = new HashMap<String, String>();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                assertEquals(4, fields.length, line);
                assertFalse(fields[0].startsWith("/"), fields[0]);
                assertFalse(fields[0].contains(".."), fields[0]);
                assertFalse(fields[1].contains("/"), fields[1]);
                assertFalse(fields[1].contains("\\"), fields[1]);
                assertTrue(fields[2].matches("[0-9a-f]{64}"), fields[0]);
                assertTrue(Set.of("CAPTURED", "SYNTHETIC", "DERIVED", "UNKNOWN").contains(fields[3]), fields[0]);
                assertNull(result.put(fields[0], fields[2]), fields[0]);
            }

            return Map.copyOf(result);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

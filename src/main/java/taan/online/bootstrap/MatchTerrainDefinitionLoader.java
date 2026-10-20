package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import taan.online.match.model.MissileTerrainDefinition;
import taan.online.match.model.TerrainLayerDefinition;
import taan.online.match.model.WeaponMissileKey;

public final class MatchTerrainDefinitionLoader {
    private static final long MAX_CELLS = 64L * 1024 * 1024;

    public Map<Integer, List<TerrainLayerDefinition>> layers(
        InputStream index,
        Function<String, InputStream> resources
    ) {
        try (index) {
            var result = new HashMap<Integer, List<TerrainLayerDefinition>>();
            long total = 0;

            for (var row : rows(index, "map_id,layer_index,left,top,width,height,resource,sha256")) {
                int map = Integer.parseInt(row[0]);
                int position = Integer.parseInt(row[1]);
                int width = Integer.parseInt(row[4]);
                int height = Integer.parseInt(row[5]);
                total += (long) width * height;
                require(total <= MAX_CELLS, "Terrain catalog exceeds cell budget");
                var layers = result.computeIfAbsent(map, ignored -> new ArrayList<>());
                require(map > 0 && position == layers.size(), "Invalid terrain layer identity or order");
                byte[] cells = raster(resources, row[6], width, height, row[7]);
                layers.add(
                    new TerrainLayerDefinition(
                        Integer.parseInt(row[2]),
                        Integer.parseInt(row[3]),
                        width,
                        height,
                        cells
                    )
                );
            }

            return result.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
            );
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot load terrain definitions", failure);
        }
    }

    public Map<WeaponMissileKey, MissileTerrainDefinition> missiles(
        InputStream index,
        Function<String, InputStream> resources
    ) {
        try (index) {
            var result = new HashMap<WeaponMissileKey, MissileTerrainDefinition>();

            for (var row : rows(index, "weapon_id,missile_type_id,width,height,center_x,center_y,resource,sha256")) {
                int weapon = Integer.parseInt(row[0]);
                int id = Integer.parseInt(row[1]);
                int width = Integer.parseInt(row[2]);
                int height = Integer.parseInt(row[3]);
                byte[] cells = raster(resources, row[6], width, height, row[7]);

                for (byte cell : cells) {
                    require(cell == 0 || cell == 1, "Invalid destruction mask value");
                }

                var definition = new MissileTerrainDefinition(
                    weapon,
                    id,
                    Integer.parseInt(row[4]),
                    Integer.parseInt(row[5]),
                    new TerrainLayerDefinition(0, 0, width, height, cells)
                );
                require(
                    result.put(new WeaponMissileKey(weapon, id), definition) == null,
                    "Duplicate missile terrain identity"
                );
            }

            require(!result.isEmpty(), "Empty weapon terrain definitions");
            return Map.copyOf(result);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot load missile terrain definitions", failure);
        }
    }

    private byte[] raster(
        Function<String, InputStream> resources,
        String name,
        int width,
        int height,
        String expectedHash
    ) throws IOException, NoSuchAlgorithmException {
        require(name.matches("terrain/[a-z0-9_/-]+\\.rle"), "Invalid terrain resource name");
        long size = (long) width * height;
        require(width > 0 && height > 0 && size <= MAX_CELLS, "Invalid terrain dimensions");

        try (var input = Objects.requireNonNull(resources.apply(name))) {
            var reader = reader(input);
            require(("TAAN_RLE_V1," + width + "," + height).equals(reader.readLine()), "Invalid terrain RLE header");
            byte[] result = new byte[(int) size];

            for (int y = 0; y < height; y++) {
                String line = reader.readLine();
                require(line != null, "Truncated terrain RLE");
                int x = 0;

                for (String run : line.split(" ", -1)) {
                    String[] pair = run.split(":", -1);
                    require(pair.length == 2, "Invalid terrain RLE run");
                    int count = Integer.parseInt(pair[0]);
                    int value = Integer.parseInt(pair[1]);
                    require(count > 0 && (long) x + count <= width, "Invalid terrain RLE run length");
                    require(value >= 0 && value <= 255, "Invalid terrain RLE byte");
                    Arrays.fill(result, y * width + x, y * width + x + count, (byte) value);
                    x += count;
                }

                require(x == width, "Incomplete terrain RLE row");
            }

            require(reader.readLine() == null, "Trailing terrain RLE rows");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result));
            require(hash.equals(expectedHash), "Terrain definition checksum mismatch");
            return result;
        }
    }

    private List<String[]> rows(InputStream input, String header) throws IOException {
        var reader = reader(Objects.requireNonNull(input));
        require(header.equals(reader.readLine()), "Invalid terrain index header");
        int columns = header.split(",").length;
        var result = new ArrayList<String[]>();
        String line;

        while ((line = reader.readLine()) != null) {
            String[] values = line.split(",", -1);
            require(values.length == columns, "Invalid terrain index columns");
            result.add(values);
        }

        return result;
    }

    private BufferedReader reader(InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()));
    }

    private void require(boolean valid, String reason) {
        if (!valid) {
            throw new IllegalArgumentException(reason);
        }
    }
}

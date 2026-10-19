package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import taan.online.match.model.CannonShotDefinition;
import taan.online.match.model.MapSpawnDefinition;
import taan.online.match.model.MatchDefinitionCatalog;
import taan.online.match.model.MatchMapDefinition;
import taan.online.match.model.WeaponDefinition;

public final class MatchDefinitionLoader {
    public MatchDefinitionCatalog load(
        InputStream properties,
        InputStream maps,
        InputStream spawns,
        InputStream weapons,
        InputStream cannon
    ) {
        return load(
            properties,
            maps,
            spawns,
            weapons,
            cannon,
            name -> getClass().getResourceAsStream("/match/" + name)
        );
    }

    public MatchDefinitionCatalog load(
        InputStream properties,
        InputStream maps,
        InputStream spawns,
        InputStream weapons,
        InputStream cannon,
        Function<String, InputStream> resources
    ) {
        try (properties; maps; spawns; weapons; cannon) {
            return definitions(properties, maps, spawns, weapons, cannon, resources);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load match definitions", failure);
        }
    }

    private MatchDefinitionCatalog definitions(
        InputStream properties,
        InputStream maps,
        InputStream spawns,
        InputStream weapons,
        InputStream cannon,
        Function<String, InputStream> resources
    ) throws IOException {
        var values = new Properties();
        values.load(new InputStreamReader(Objects.requireNonNull(properties), StandardCharsets.UTF_8.newDecoder()));

        if (!values.stringPropertyNames().equals(Set.of("catalog.version"))) {
            throw new IllegalArgumentException("Invalid match catalog properties");
        }

        var points = csv(
            spawns,
            "map_id,index,raw_x,raw_y,settled_x,settled_y,side,flag,valid",
            row -> Map.entry(
                Integer.parseInt(row[0]),
                new MapSpawnDefinition(
                    Integer.parseInt(row[1]),
                    Integer.parseInt(row[2]),
                    Integer.parseInt(row[3]),
                    Integer.parseInt(row[4]),
                    Integer.parseInt(row[5]),
                    Integer.parseInt(row[6]),
                    Integer.parseInt(row[7]),
                    valid(row[8])
                )
            )
        ).stream().collect(
            Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
        );
        var terrain = new MatchTerrainDefinitionLoader();
        var layers = terrain.layers(resources.apply("terrain-layers.csv"), resources);
        var missiles = terrain.missiles(resources.apply("missile-terrain.csv"), resources);
        var definitions = new HashMap<Integer, MatchMapDefinition>();

        for (var map : csv(
            maps,
            "map_id,asset,width,height,kind_0,kind_1,kind_4,kind_6",
            row -> new MatchMapDefinition(
                Integer.parseInt(row[0]),
                row[1],
                Integer.parseInt(row[2]),
                Integer.parseInt(row[3]),
                points.getOrDefault(Integer.parseInt(row[0]), List.of()),
                kinds(row),
                layers.getOrDefault(Integer.parseInt(row[0]), List.of())
            )
        )) {
            if (definitions.put(map.mapId(), map) != null) {
                throw new IllegalArgumentException("Duplicate match map");
            }
        }

        var expected = IntStream.rangeClosed(1, 21).boxed().collect(Collectors.toSet());

        if (!definitions.keySet().equals(expected) || !points.keySet().equals(expected)
            || !layers.keySet().equals(expected)) {
            throw new IllegalArgumentException("Incomplete ordinary map catalog");
        }

        var weaponDefinitions = csv(
            weapons,
            "weapon_id,asset,resource_max_hp,max_energy,resource_move",
            row -> new WeaponDefinition(
                Integer.parseInt(row[0]),
                row[1],
                Integer.parseInt(row[2]),
                Integer.parseInt(row[3]),
                Integer.parseInt(row[4])
            )
        );
        var shots = csv(
            cannon,
            "weapon_id,attack_kind,min_power,max_power,missile_variant,missile_type_id",
            row -> new CannonShotDefinition(
                Integer.parseInt(row[0]),
                Integer.parseInt(row[1]),
                Integer.parseInt(row[2]),
                Integer.parseInt(row[3]),
                Integer.parseInt(row[4]),
                Integer.parseInt(row[5])
            )
        );

        if (shots.size() != 1) {
            throw new IllegalArgumentException("Incomplete ordinary cannon definitions");
        }

        return new MatchDefinitionCatalog(
            values.getProperty("catalog.version"),
            definitions,
            weaponDefinitions,
            shots.getFirst(),
            missiles
        );
    }

    private Set<Integer> kinds(String[] row) {
        int[] kinds = {0, 1, 4, 6};
        var result = new HashSet<Integer>();

        for (int index = 0; index < kinds.length; index++) {
            if (valid(row[index + 4])) {
                result.add(kinds[index]);
            }
        }

        return result;
    }

    private <T> List<T> csv(InputStream input, String header, Function<String[], T> mapper) throws IOException {
        var reader = new BufferedReader(
            new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8.newDecoder())
        );

        if (!header.equals(reader.readLine())) {
            throw new IllegalArgumentException("Invalid match definition header");
        }

        int columns = header.split(",").length;
        var rows = new ArrayList<T>();
        String line;

        while ((line = reader.readLine()) != null) {
            String[] values = line.split(",", -1);

            if (values.length != columns) {
                throw new IllegalArgumentException("Invalid match definition column count");
            }

            rows.add(mapper.apply(values));
        }

        return List.copyOf(rows);
    }

    private boolean valid(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid map definition flag");
        };
    }
}

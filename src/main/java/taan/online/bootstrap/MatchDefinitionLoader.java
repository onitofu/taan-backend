package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import taan.online.match.model.MapSpawnDefinition;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchMapDefinition;
import taan.online.match.model.WeaponDefinition;

public final class MatchDefinitionLoader {
    public MatchDefinitions load(InputStream properties, InputStream spawns, InputStream weapons) {
        try (properties; spawns; weapons) {
            return definitions(properties, spawns, weapons);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load match definitions", failure);
        }
    }

    private MatchDefinitions definitions(InputStream properties, InputStream spawns, InputStream weapons)
        throws IOException {
        var values = properties(properties);
        var map = new MatchMapDefinition(
            Integer.parseInt(values.getProperty("map.id")),
            values.getProperty("map.asset"),
            Integer.parseInt(values.getProperty("map.width")),
            Integer.parseInt(values.getProperty("map.height")),
            csv(
                spawns,
                "index,raw_x,raw_y,settled_x,settled_y,side,flag,valid",
                row -> new MapSpawnDefinition(
                    Integer.parseInt(row[0]),
                    Integer.parseInt(row[1]),
                    Integer.parseInt(row[2]),
                    Integer.parseInt(row[3]),
                    Integer.parseInt(row[4]),
                    Integer.parseInt(row[5]),
                    Integer.parseInt(row[6]),
                    valid(row[7])
                )
            )
        );
        var definitions = csv(
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
        return new MatchDefinitions(values.getProperty("catalog.version"), map, definitions);
    }

    private Properties properties(InputStream input) throws IOException {
        var values = new Properties();
        var reader = new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8.newDecoder());
        values.load(reader);

        if (!values.stringPropertyNames().equals(Set.of(
            "catalog.version",
            "map.id",
            "map.asset",
            "map.width",
            "map.height"
        ))) {
            throw new IllegalArgumentException("Invalid match map properties");
        }

        return values;
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
            default -> throw new IllegalArgumentException("Invalid map spawn validity");
        };
    }
}

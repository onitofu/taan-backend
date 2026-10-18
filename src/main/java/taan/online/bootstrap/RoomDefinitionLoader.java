package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomMapCatalog;
import taan.online.room.model.RoomTitleFilter;

public final class RoomDefinitionLoader {
    public RoomBootstrapDefinition defaults(InputStream input, RoomMapCatalog maps) {
        var values = new Properties();

        try (var source = Objects.requireNonNull(input)) {
            values.load(source);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load room defaults", failure);
        }

        if (!values.stringPropertyNames().equals(Set.of("map.id", "weapon.id"))) {
            throw new IllegalArgumentException("Invalid room defaults");
        }

        var result = new RoomBootstrapDefinition(
            Integer.parseInt(values.getProperty("map.id")),
            Integer.parseInt(values.getProperty("weapon.id"))
        );

        if (!maps.contains(result.mapId())) {
            throw new IllegalArgumentException("Room default map is not selectable");
        }

        return result;
    }

    public RoomMapCatalog maps(InputStream input) {
        try (var reader = new BufferedReader(
            new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8.newDecoder())
        )) {
            if (!"map_id".equals(reader.readLine())) {
                throw new IllegalArgumentException("Invalid room map catalog header");
            }

            var ids = new HashSet<Integer>();
            String line;

            while ((line = reader.readLine()) != null) {
                if (!ids.add(Integer.parseInt(line))) {
                    throw new IllegalArgumentException("Duplicate room map");
                }
            }

            return new RoomMapCatalog(ids);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load room map catalog", failure);
        }
    }

    public RoomTitleFilter titleFilter(InputStream input) {
        try (var reader = new BufferedReader(
            new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8.newDecoder())
        )) {
            var entries = new ArrayList<String>();
            String line;

            while ((line = reader.readLine()) != null) {
                entries.add(line);
            }

            if (entries.size() != 2962) {
                throw new IllegalArgumentException("Incomplete room title filter");
            }

            return new RoomTitleFilter(entries);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load room title filter", failure);
        }
    }
}

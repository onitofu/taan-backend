package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomTitleFilter;

public final class RoomDefinitionLoader {
    public RoomBootstrapDefinition defaults(InputStream input) {
        var values = new Properties();

        try (var source = Objects.requireNonNull(input)) {
            values.load(source);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load room defaults", failure);
        }

        if (!values.stringPropertyNames().equals(Set.of("map.id", "weapon.id"))) {
            throw new IllegalArgumentException("Invalid room defaults");
        }

        return new RoomBootstrapDefinition(
            Integer.parseInt(values.getProperty("map.id")),
            Integer.parseInt(values.getProperty("weapon.id"))
        );
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

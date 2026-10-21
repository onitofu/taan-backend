package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.LevelDefinition;

public final class CharacterLevelLoader {
    public CharacterLevelTable load(InputStream input) {
        Objects.requireNonNull(input, "Missing level table");

        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!"level,total_exp,exp_to_next,reference_hours".equals(reader.readLine())) {
                throw new IllegalArgumentException("Invalid level table header");
            }

            var levels = new ArrayList<LevelDefinition>();
            String line;

            while ((line = reader.readLine()) != null) {
                var fields = line.split(",", -1);

                if (fields.length != 4) {
                    throw new IllegalArgumentException("Invalid level table row");
                }

                levels.add(new LevelDefinition(Integer.parseInt(fields[0]), Long.parseLong(fields[1])));
            }

            return new CharacterLevelTable(levels);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot read level table", failure);
        }
    }
}

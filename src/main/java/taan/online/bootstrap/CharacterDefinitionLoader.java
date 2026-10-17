package taan.online.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import taan.online.account.model.AccountGender;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterCreationOptions;
import taan.online.character.model.CharacterNameFilter;

public final class CharacterDefinitionLoader {
    public CharacterNameFilter nameFilter(InputStream input) {
        try (var reader = reader(input)) {
            var entries = new ArrayList<String>();
            String line;

            while ((line = reader.readLine()) != null) {
                entries.add(line);
            }

            if (entries.size() != 2962) {
                throw new IllegalArgumentException("Incomplete character name filter");
            }

            return new CharacterNameFilter(entries);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load character name filter", failure);
        }
    }

    public CharacterCreationOptions creationOptions(InputStream input) {
        var options = new EnumMap<AccountGender, Set<AppearancePart>>(AccountGender.class);

        for (var gender : AccountGender.values()) {
            options.put(gender, new HashSet<>());
        }

        try (var reader = reader(input)) {
            if (!"gender,slot,item_id,parameter0,parameter1,parameter2".equals(reader.readLine())) {
                throw new IllegalArgumentException("Invalid character creation header");
            }

            String line;

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", -1);

                if (columns.length != 6) {
                    throw new IllegalArgumentException("Invalid character creation row");
                }

                AccountGender gender = switch (Integer.parseInt(columns[0])) {
                    case 1 -> AccountGender.MALE;
                    case 2 -> AccountGender.FEMALE;
                    default -> throw new IllegalArgumentException("Invalid creation option gender");
                };
                var part = new AppearancePart(
                    Integer.parseInt(columns[1]),
                    Long.parseLong(columns[2]),
                    Integer.parseInt(columns[3]),
                    Integer.parseInt(columns[4]),
                    Integer.parseInt(columns[5])
                );

                if (!options.get(gender).add(part)) {
                    throw new IllegalArgumentException("Duplicate character creation option");
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load character creation options", failure);
        }

        return new CharacterCreationOptions(options);
    }

    private BufferedReader reader(InputStream input) {
        return new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(input),
                StandardCharsets.UTF_8.newDecoder()
            )
        );
    }
}

package taan.online.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;

class CharacterCreationOptionsTest {
    @Test
    void acceptsAll648ShippedCombinationsWithoutMixingGenderOrParameterComponents() throws Exception {
        var policy = CharacterFixtures.options();

        for (var gender : AccountGender.values()) {
            List<AppearancePart> choices = choices(gender);
            int count = 0;

            for (var hair : slot(choices, 1)) {
                for (var eye : slot(choices, 3)) {
                    for (var mouth : slot(choices, 5)) {
                        for (var cloth : slot(choices, 8)) {
                            var parts = new ArrayList<AppearancePart>();

                            for (int slot = 0; slot < 16; slot++) {
                                parts.add(new AppearancePart(slot, 0, 0, 0, 0));
                            }

                            for (var part : List.of(hair, eye, mouth, cloth)) {
                                parts.set(part.slot(), part);
                            }

                            var appearance = new CharacterAppearance(parts);
                            var other = gender == AccountGender.MALE ? AccountGender.FEMALE : AccountGender.MALE;

                            assertTrue(policy.allows(gender, appearance));
                            assertFalse(policy.allows(other, appearance));

                            count++;
                        }
                    }
                }
            }

            assertEquals(gender == AccountGender.MALE ? 162 : 486, count);
        }

        var mixed = new ArrayList<>(CharacterFixtures.appearance(AccountGender.MALE).parts());
        mixed.set(5, new AppearancePart(5, 725, 266, 303, 279));

        assertFalse(policy.allows(AccountGender.MALE, new CharacterAppearance(mixed)));
    }

    private List<AppearancePart> choices(AccountGender gender) throws Exception {
        try (var input = CharacterFixtures.class.getResourceAsStream("/character/creation-options.csv");
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String prefix = gender == AccountGender.MALE ? "1," : "2,";
            return reader.lines().skip(1).filter(line -> line.startsWith(prefix)).map(
                line -> {
                    var values = line.split(",");
                    return new AppearancePart(
                        Integer.parseInt(values[1]),
                        Long.parseLong(values[2]),
                        Integer.parseInt(values[3]),
                        Integer.parseInt(values[4]),
                        Integer.parseInt(values[5])
                    );
                }
            ).toList();
        }
    }

    private List<AppearancePart> slot(List<AppearancePart> choices, int slot) {
        return choices.stream().filter(part -> part.slot() == slot).toList();
    }
}

package taan.online.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.service.model.CreateCharacterCommand;

class CharacterModelTest {
    @Test
    void preservesDisplayCaseAndEnforcesTheVnNameBoundariesWithoutLeakingInput() {
        for (String name : List.of("Luma", "A_[]", "a`^z", "A\\Z0", "ÁắỸz", "a".repeat(18))) {
            assertEquals(name, new CharacterName(name).value());
            assertFalse(new CharacterName(name).toString().contains(name));
        }

        assertNotEquals(new CharacterName("MaleHero"), new CharacterName("malehero"));

        for (String name : List.of("", "abc", "a".repeat(19), " abcd", "ab c", "Тест", "A😀z", "ab\0c", "a-z0")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new CharacterName(name));

            assertFalse(failure.getMessage().contains("Тест"));
        }

        for (int unit : new int[]{0x30, 0x39, 0x41, 0x7a, 0xc0, 0x17f, 0x180, 0x236, 0x1ea0, 0x1ef9}) {
            String name = String.valueOf((char) unit).repeat(4);

            assertEquals(name, new CharacterName(name).value());
        }

        for (int unit : new int[]{0x2f, 0x3a, 0x40, 0x7b, 0xbf, 0x237, 0x1e9f, 0x1efa, 0xd800}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new CharacterName(String.valueOf((char) unit).repeat(4))
            );
        }
    }

    @Test
    void boundsIdentitiesPartsAndLevelsAndKeepsSnapshotsImmutable() {
        assertEquals(0xffff_ffffL, new CharacterId(0xffff_ffffL).value());

        for (long id : new long[]{0, -1, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> new CharacterId(id));
        }

        assertEquals(0xffff, new AppearancePart(15, 0xffff_ffffL, 0xffff, 0xffff, 0xffff).palette3Id());

        for (int slot : new int[]{-1, 16}) {
            assertThrows(IllegalArgumentException.class, () -> new AppearancePart(slot, 0, 0, 0, 0));
        }

        for (long item : new long[]{-1, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> new AppearancePart(0, item, 0, 0, 0));
        }

        for (int parameter : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new AppearancePart(0, 0, parameter, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new AppearancePart(0, 0, 0, parameter, 0));
            assertThrows(IllegalArgumentException.class, () -> new AppearancePart(0, 0, 0, 0, parameter));
        }

        var parts = new ArrayList<>(CharacterFixtures.appearance(AccountGender.MALE).parts());
        Collections.reverse(parts);
        var appearance = new CharacterAppearance(parts);
        parts.clear();

        assertEquals(16, appearance.parts().size());
        assertEquals(0, appearance.parts().getFirst().slot());
        assertThrows(UnsupportedOperationException.class, () -> appearance.parts().clear());
        assertThrows(IllegalArgumentException.class, () -> new CharacterAppearance(List.of()));

        var duplicate = new ArrayList<>(appearance.parts());
        duplicate.set(15, duplicate.getFirst());

        assertThrows(IllegalArgumentException.class, () -> new CharacterAppearance(duplicate));

        for (int level : new int[]{0, 110, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> character(level, appearance));
        }

        assertEquals(109, character(109, appearance).level());
        assertFalse(character(1, appearance).toString().contains("MaleHero"));
        assertFalse(new CreateCharacterCommand("MaleHero", appearance).toString().contains("MaleHero"));
    }

    @Test
    void filtersLiteralSubstringsWithoutTrimmingOrUsingTheDefaultLocale() {
        var entries = new ArrayList<>(List.of("admin", "ab*cd", "hero "));
        var filter = new CharacterNameFilter(entries);
        entries.clear();

        assertTrue(filter.forbids(new CharacterName("xxAdMiNxx")));
        assertFalse(filter.forbids(new CharacterName("Abcd")));
        assertFalse(filter.forbids(new CharacterName("Hero")));
        assertThrows(IllegalArgumentException.class, () -> new CharacterNameFilter(List.of("")));

        Locale original = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertTrue(filter.forbids(new CharacterName("ADMIN")));
            assertTrue(CharacterFixtures.filter().forbids(new CharacterName("AdMiN")));
            assertFalse(CharacterFixtures.filter().forbids(new CharacterName("Luma")));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void requiresCompleteCreationDefinitionsAndExactTuplesForTheAccountGender() {
        var male = CharacterFixtures.appearance(AccountGender.MALE);
        var female = CharacterFixtures.appearance(AccountGender.FEMALE);
        var options = CharacterFixtures.options();

        assertTrue(options.allows(AccountGender.MALE, male));
        assertTrue(options.allows(AccountGender.FEMALE, female));
        assertFalse(options.allows(AccountGender.FEMALE, male));
        assertFalse(options.allows(AccountGender.MALE, female));

        for (int slot : new int[]{0, 1, 3, 5, 8, 15}) {
            var changed = new ArrayList<>(male.parts());
            changed.set(slot, new AppearancePart(slot, 0xffff_ffffL, 0, 0, 0));

            assertFalse(options.allows(AccountGender.MALE, new CharacterAppearance(changed)));

            changed.set(slot, new AppearancePart(slot, 0, 0, 0, 0));

            if (slot != 0 && slot != 15) {
                assertFalse(options.allows(AccountGender.MALE, new CharacterAppearance(changed)));
            }
        }

        var definitions = definitions(male, female);
        definitions.get(AccountGender.MALE).add(new AppearancePart(0, 1, 0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> new CharacterCreationOptions(definitions));

        var zeroItem = definitions(male, female);
        zeroItem.get(AccountGender.MALE).add(new AppearancePart(1, 0, 0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> new CharacterCreationOptions(zeroItem));

        var missing = definitions(male, female);
        missing.get(AccountGender.FEMALE).removeIf(part -> part.slot() == 3);

        assertThrows(IllegalArgumentException.class, () -> new CharacterCreationOptions(missing));

        var copied = definitions(male, female);
        var immutable = new CharacterCreationOptions(copied);
        copied.get(AccountGender.MALE).clear();

        assertTrue(immutable.allows(AccountGender.MALE, male));
    }

    private GameCharacter character(int level, CharacterAppearance appearance) {
        return new GameCharacter(
            new CharacterId(1),
            new AccountId(1),
            new CharacterName("MaleHero"),
            level,
            AccountGender.MALE,
            appearance,
            Instant.EPOCH
        );
    }

    private Map<AccountGender, Set<AppearancePart>> definitions(
        CharacterAppearance male,
        CharacterAppearance female
    ) {
        var result = new EnumMap<AccountGender, Set<AppearancePart>>(AccountGender.class);
        result.put(AccountGender.MALE, new HashSet<>(male.parts().stream().filter(p -> p.itemId() != 0).toList()));
        result.put(AccountGender.FEMALE, new HashSet<>(female.parts().stream().filter(p -> p.itemId() != 0).toList()));
        return result;
    }
}

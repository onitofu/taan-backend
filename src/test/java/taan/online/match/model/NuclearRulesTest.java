package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class NuclearRulesTest {
    @Test
    void matchesIndependentNativePersonalSelectionIncludingCaptainPrefixAndDisplacedCards() throws Exception {
        try (var input = getClass().getResourceAsStream("/match/nuclear/selection.tsv")) {
            var rows = new String(input.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1).toList();
            assertEquals(44, rows.size());

            for (var row : rows) {
                var fields = row.split("\t", -1);
                var selected = MatchItemSelectionRules.selected(items(fields[1]), fields[0].equals("1"));
                assertEquals(items(fields[2]), selected, row);
                assertEquals(Integer.parseInt(fields[3]), MatchItemSelectionRules.attackKind(selected), row);
                assertEquals(Integer.parseInt(fields[4]), MatchItemSelectionRules.effectiveWeaponId(0, selected), row);
                assertEquals(Integer.parseInt(fields[5]), MatchItemSelectionRules.shotLimit(selected), row);
            }
        }
    }

    @Test
    void loadsSparseDefinitionsAndOriginalDestructionMasksWithoutChangingBaseWeapons() {
        var definitions = MatchFixtures.definitions();
        assertEquals(13, definitions.weapons().size());
        assertEquals(5000, definitions.weapons().get(100).resourceMove());
        assertEquals(4000, definitions.weapons().get(3).resourceMove());
        assertEquals(6000, definitions.weapons().get(10).resourceMove());

        for (int kind : new int[]{0, 1}) {
            var attack = definitions.attacks().get(new WeaponAttackKey(100, kind));
            var missile = definitions.missiles().get(attack.fires().getFirst().missileKey(100));
            assertEquals(512, missile.mask().width());
            assertEquals(512, missile.mask().height());
            assertEquals(256, missile.centerX());
            assertEquals(kind == 0 ? 276 : 278, missile.centerY());
            assertEquals(1, missile.mask().value(missile.centerX(), missile.centerY()));
        }

        var attacks = new HashMap<>(definitions.attacks());
        attacks.put(new WeaponAttackKey(100, 2), attacks.remove(new WeaponAttackKey(100, 1)));
        assertThrows(IllegalArgumentException.class, () -> new MatchDefinitions(
            definitions.catalogVersion(),
            definitions.map(),
            definitions.weapons(),
            attacks,
            definitions.missiles()
        ));
    }

    private List<Integer> items(String text) {
        return text.isEmpty() ? List.of() : Arrays.stream(text.split(",")).map(Integer::valueOf).toList();
    }
}

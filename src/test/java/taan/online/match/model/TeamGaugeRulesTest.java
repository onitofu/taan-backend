package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.FixtureManifest;

class TeamGaugeRulesTest {
    @Test
    void matchesTheResearchersIndependentNativeThresholdAndLevelVectors() throws Exception {
        String name = "match/no-item/damage-gauge-native.tsv";

        try (var input = getClass().getResourceAsStream("/" + name)) {
            byte[] bytes = input.readAllBytes();
            FixtureManifest.verify(name, bytes);

            for (var line : new String(bytes, StandardCharsets.UTF_8).lines().skip(1).toList()) {
                String[] row = line.split("\t");
                int count = Integer.parseInt(row[1]);
                int value = Integer.parseInt(row[2]);
                int actual = row[0].equals("LEVEL") ? TeamGaugeRules.level(count, value)
                    : TeamGaugeRules.threshold(count, value);
                assertEquals(Integer.parseInt(row[3]), actual, line);
            }
        }
    }

    @Test
    void keepsGenerationSeparateFromReplayAndDoesNotClampOrSpendTheGauge() {
        var state = new TeamGaugeState(90, 95);
        var first = TeamGaugeRules.generate(state, 2, 0, 50).orElseThrow();
        assertEquals(new TeamGaugeDelta(0, 145, 95), first);
        assertEquals(new TeamGaugeState(145, 145), TeamGaugeRules.replay(first));
        assertEquals(new TeamGaugeState(90, 95), state);
        assertEquals(404, TeamGaugeRules.generate(new TeamGaugeState(399, 399), 2, 4, 5)
            .orElseThrow().newGauge());
        assertTrue(TeamGaugeRules.generate(new TeamGaugeState(400, 400), 2, 4, 5).isEmpty());
        assertEquals(4, TeamGaugeRules.generate(new TeamGaugeState(0, 65535), 2, 4, 5).orElseThrow().newGauge());
        assertEquals(new TeamGaugeState(65535, 0), new TeamGaugeState(-1, 65536));
        assertEquals(new TeamGaugeDelta(255, 0, 65535), new TeamGaugeDelta(255, 65536, -1));
    }

    @Test
    void selectsTheExactFreeItemsAndRejectsInvalidRuleInputs() {
        var items = List.of(List.of(), List.of(1030), List.of(1), List.of(2), List.of(2, 3));

        for (int level = 0; level < items.size(); level++) {
            assertEquals(items.get(level), TeamGaugeRules.items(level));
        }

        assertEquals(1000, TeamGaugeRules.threshold(2, -1));
        assertThrows(IllegalArgumentException.class, () -> TeamGaugeRules.level(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> TeamGaugeRules.level(9, 0));
        assertThrows(IllegalArgumentException.class, () -> TeamGaugeRules.items(-1));
        assertThrows(IllegalArgumentException.class, () -> new TeamGaugeDelta(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TeamGaugeDelta(256, 0, 0));
        assertEquals(List.of(1, 2, 3, 5), MatchItemSelectionRules.selected(List.of(5), true, List.of(2, 3)));
    }
}

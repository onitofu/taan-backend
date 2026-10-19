package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MatchEnergyRulesTest {
    @Test
    void usesSignedWordArithmeticAndClampsAfterEveryPass() {
        assertEquals(100, MatchEnergyRules.adjust(95, 10, 100));
        assertEquals(0, MatchEnergyRules.adjust(32760, 10, 100));
        assertEquals(0, MatchEnergyRules.adjust(0, 65535, 100));
        assertEquals(40, MatchEnergyRules.adjust(30, 10, 100));
        assertEquals(10, MatchEnergyRules.maximum(10, 65535));
        assertEquals(15, MatchEnergyRules.maximum(10, 15));
        assertEquals(100, MatchEnergyRules.adjust(MatchEnergyRules.adjust(95, 10, 100), 10, 100));
    }
}

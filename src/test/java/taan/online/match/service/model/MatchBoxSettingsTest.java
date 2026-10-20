package taan.online.match.service.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchBoxPolicy;

class MatchBoxSettingsTest {
    @Test
    void keepsOrdinaryConstructorsDisabledAndDowngradesUnsupportedProfiles() {
        assertEquals(MatchBoxPolicy.DISABLED, new MatchBootstrapSettings(30, 20, 8).boxPolicy());
        var defaults = new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CLIENT_SCENARIO);
        assertEquals(MatchBoxPolicy.DISABLED, defaults.boxPolicy());
        var controlled = new MatchBootstrapSettings(
            30,
            20,
            8,
            MatchExecutionMode.CLIENT_SCENARIO,
            20,
            10,
            MatchLootPolicy.disabled(),
            MatchBoxPolicy.ENABLED
        );
        assertSame(controlled, controlled.forProfile(true));
        var preview = controlled.forProfile(false);
        assertEquals(MatchBoxPolicy.DISABLED, preview.boxPolicy());
        assertEquals(MatchExecutionMode.PREVIEW, preview.executionMode());
        assertThrows(NullPointerException.class, () -> new MatchBootstrapSettings(
            30,
            20,
            8,
            MatchExecutionMode.CLIENT_SCENARIO,
            20,
            10,
            MatchLootPolicy.disabled(),
            null
        ));
    }
}

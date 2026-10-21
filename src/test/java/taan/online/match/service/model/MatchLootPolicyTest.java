package taan.online.match.service.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchItemSelectionRules;

class MatchLootPolicyTest {
    @Test
    void enablesSixteenSupportedItemsWhilePreservingTheNativeChannelThresholds() {
        var policy = MatchLootPolicy.nativeItems();
        assertEquals(
            Set.of(1, 2, 3, 4, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050),
            policy.allowedItemIds()
        );

        for (int item : policy.allowedItemIds()) {
            assertTrue(MatchItemSelectionRules.supports(item));
            assertTrue(policy.allowsUse(item));

            for (int scalar : new int[]{0, 1, 49, 50, 69, 70, 89, 90, 100}) {
                boolean eligible = item == 4 ? scalar >= 90 : item == 2 ? scalar >= 70 : item != 3 || scalar >= 50;
                assertEquals(eligible, policy.permits(item, scalar));
            }

            assertFalse(policy.permits(item, -1));
            assertFalse(policy.permits(item, 101));
        }

        assertFalse(policy.permits(4, 89));
        assertTrue(policy.permits(4, 90));
        assertThrows(UnsupportedOperationException.class, () -> policy.allowedItemIds().clear());
    }

    @Test
    void keepsExplicitSubsetsAndDisabledOrPreviewProfilesRestricted() {
        var policy = new MatchLootPolicy(Set.of(1, 2, 3));
        assertTrue(policy.enabled());
        assertTrue(policy.allowsUse(1));
        assertTrue(policy.allowsUse(2));
        assertTrue(policy.allowsUse(3));
        assertFalse(policy.allowsUse(5));
        assertTrue(policy.permits(1, 0));
        assertFalse(policy.permits(2, 69));
        assertTrue(policy.permits(2, 70));
        assertFalse(policy.permits(3, 49));
        assertTrue(policy.permits(3, 50));
        assertFalse(policy.permits(5, 70));
        assertFalse(MatchLootPolicy.disabled().enabled());
        assertFalse(MatchLootPolicy.disabled().allowsUse(2));
        assertFalse(MatchLootPolicy.disabled().permits(5, 0));

        for (int item : new int[]{0, 8, 6, 1000, 1006, 1055, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> new MatchLootPolicy(Set.of(item)));
        }

        var preview = new MatchBootstrapSettings(30, 20, 8);
        assertEquals(MatchExecutionMode.PREVIEW, preview.executionMode());
        assertFalse(preview.lootPolicy().enabled());
        var normal = new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CLIENT_SCENARIO);
        assertFalse(normal.lootPolicy().enabled());
        var controlled = new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CLIENT_SCENARIO, 20, 10, policy);
        assertEquals(policy, controlled.forProfile(true).lootPolicy());
        assertFalse(controlled.forProfile(false).lootPolicy().enabled());
        assertThrows(NullPointerException.class, () -> new MatchBootstrapSettings(
            30,
            20,
            8,
            MatchExecutionMode.CLIENT_SCENARIO,
            20,
            10,
            null
        ));
    }

    @Test
    void allowsEveryDamageCardAcrossAllSupportedChannelValuesWithoutExtendingDisabledProfiles() {
        for (int item = 1005; item <= 1050; item += 5) {
            var policy = new MatchLootPolicy(Set.of(item));
            assertTrue(policy.allowsUse(item));
            assertTrue(policy.permits(item, 0));
            assertTrue(policy.permits(item, 89));
            assertFalse(MatchLootPolicy.disabled().allowsUse(item));
            assertFalse(MatchLootPolicy.disabled().permits(item, 89));
        }
    }
}

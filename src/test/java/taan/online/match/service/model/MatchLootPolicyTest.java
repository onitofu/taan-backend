package taan.online.match.service.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import taan.online.match.model.MatchItemSelectionRules;
import org.junit.jupiter.api.Test;

class MatchLootPolicyTest {
    @Test
    void enablesOnlyTheThirteenImplementedNativeScalarZeroItems() {
        var policy = MatchLootPolicy.minimalCannon();
        assertEquals(
            Set.of(1, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050),
            policy.allowedItemIds()
        );

        for (int item : policy.allowedItemIds()) {
            assertTrue(MatchItemSelectionRules.supports(item));
            assertTrue(policy.allowsUse(item));
            assertTrue(policy.permits(item, 0));
            assertFalse(policy.permits(item, 1));
        }

        assertFalse(policy.permits(2, 0));
        assertFalse(policy.permits(3, 0));
        assertFalse(policy.permits(4, 0));
        assertThrows(UnsupportedOperationException.class, () -> policy.allowedItemIds().clear());
    }

    @Test
    void restrictsControlledFixturesToTheThirteenScalarZeroCandidatesAndDisablesNormalMatches() {
        var policy = new MatchLootPolicy(Set.of(1, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050));
        assertTrue(policy.enabled());

        for (int item : policy.allowedItemIds()) {
            assertTrue(policy.permits(item, 0));
            assertFalse(policy.permits(item, 1));
        }

        assertFalse(policy.permits(2, 0));
        var repeat = new MatchLootPolicy(Set.of(1, 2));
        assertTrue(repeat.allowsUse(1));
        assertTrue(repeat.allowsUse(2));
        assertFalse(repeat.allowsUse(5));
        assertTrue(repeat.permits(1, 0));
        assertFalse(repeat.permits(2, 0));
        assertFalse(repeat.permits(2, 70));
        assertFalse(MatchLootPolicy.disabled().allowsUse(2));
        assertFalse(MatchLootPolicy.disabled().enabled());
        assertFalse(MatchLootPolicy.disabled().permits(5, 0));

        for (int item : new int[]{0, 4, 6, 1000, 1006, 1055, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> new MatchLootPolicy(Set.of(item)));
        }

        var preview = new MatchBootstrapSettings(30, 20, 8);
        assertEquals(MatchExecutionMode.PREVIEW, preview.executionMode());
        assertFalse(preview.lootPolicy().enabled());
        var normal = new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CANNON_1V1);
        assertFalse(normal.lootPolicy().enabled());
        var controlled = new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CANNON_1V1, 20, 10, policy);
        assertEquals(policy, controlled.forProfile(true).lootPolicy());
        assertFalse(controlled.forProfile(false).lootPolicy().enabled());
        assertThrows(NullPointerException.class, () -> new MatchBootstrapSettings(
            30,
            20,
            8,
            MatchExecutionMode.CANNON_1V1,
            20,
            10,
            null
        ));
    }

    @Test
    void allowsEveryMinimalDamageCardOnlyInTheExplicitControlledProfile() {
        for (int item = 1005; item <= 1050; item += 5) {
            var policy = new MatchLootPolicy(Set.of(item));
            assertTrue(policy.allowsUse(item));
            assertTrue(policy.permits(item, 0));
            assertFalse(policy.permits(item, 1));
            assertFalse(MatchLootPolicy.disabled().allowsUse(item));
            assertFalse(MatchLootPolicy.disabled().permits(item, 0));
        }
    }

    @Test
    void permitsSpreadUseInControlledFixturesWithoutAddingItToScalarZeroLoot() {
        var policy = new MatchLootPolicy(Set.of(1, 2, 3));
        assertTrue(policy.allowsUse(3));
        assertFalse(policy.permits(3, 0));
        assertFalse(policy.permits(3, 50));
        assertFalse(MatchLootPolicy.disabled().allowsUse(3));
        assertTrue(policy.permits(1, 0));
        assertFalse(policy.permits(2, 0));
    }

}

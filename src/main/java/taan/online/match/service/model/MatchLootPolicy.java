package taan.online.match.service.model;

import java.util.Set;
import taan.online.match.model.MatchNativeLootRules;

public record MatchLootPolicy(Set<Integer> allowedItemIds) {
    public MatchLootPolicy {
        allowedItemIds = Set.copyOf(allowedItemIds);

        if (allowedItemIds.stream().anyMatch(item -> !candidate(item))) {
            throw new IllegalArgumentException("Unsupported controlled battle item");
        }
    }

    public static MatchLootPolicy disabled() {
        return new MatchLootPolicy(Set.of());
    }

    public static MatchLootPolicy nativeItems() {
        return new MatchLootPolicy(
            Set.of(
                1,
                2,
                3,
                4,
                5,
                7,
                1005,
                1010,
                1015,
                1020,
                1025,
                1030,
                1035,
                1040,
                1045,
                1050
            )
        );
    }

    public boolean enabled() {
        return !allowedItemIds.isEmpty();
    }

    public boolean permits(int itemId, int scalar) {
        return allowedItemIds.contains(itemId) && MatchNativeLootRules.eligible(itemId, scalar);
    }

    public boolean allowsUse(int itemId) {
        return allowedItemIds.contains(itemId);
    }

    private static boolean candidate(int item) {
        return MatchNativeLootRules.eligible(item, 100);
    }
}

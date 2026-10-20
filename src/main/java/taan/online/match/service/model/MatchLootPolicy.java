package taan.online.match.service.model;

import java.util.Set;

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

    public boolean enabled() {
        return !allowedItemIds.isEmpty();
    }

    public boolean permits(int itemId, int scalar) {
        return scalar == 0 && allowedItemIds.contains(itemId);
    }

    private static boolean candidate(int item) {
        return item == 1 || item == 5 || item == 7 || item >= 1005 && item <= 1050 && item % 5 == 0;
    }
}

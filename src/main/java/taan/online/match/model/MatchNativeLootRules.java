package taan.online.match.model;

import java.util.List;

public final class MatchNativeLootRules {
    private static final List<MatchLootItemDefinition> ITEMS = List.of(
        new MatchLootItemDefinition(1, 0, 100, 30, 75),
        new MatchLootItemDefinition(2, 70, 100, 10, 40),
        new MatchLootItemDefinition(3, 50, 100, 10, 40),
        new MatchLootItemDefinition(1005, 0, 100, 33, 37),
        new MatchLootItemDefinition(1010, 0, 100, 30, 35),
        new MatchLootItemDefinition(1015, 0, 100, 27, 31),
        new MatchLootItemDefinition(1020, 0, 100, 24, 28),
        new MatchLootItemDefinition(1025, 0, 100, 21, 25),
        new MatchLootItemDefinition(1030, 0, 100, 18, 22),
        new MatchLootItemDefinition(1035, 0, 100, 15, 19),
        new MatchLootItemDefinition(1040, 0, 100, 12, 16),
        new MatchLootItemDefinition(1045, 0, 100, 9, 13),
        new MatchLootItemDefinition(1050, 0, 100, 6, 10),
        new MatchLootItemDefinition(5, 0, 100, 60, 75),
        new MatchLootItemDefinition(4, 90, 100, 30, 75),
        new MatchLootItemDefinition(7, 0, 100, 15, 50)
    );

    private MatchNativeLootRules() {
    }

    public static boolean eligible(int itemId, int scalar) {
        return scalar >= 0 && scalar <= 100
            && ITEMS.stream().anyMatch(item -> item.itemId() == itemId && item.weight(scalar) > 0);
    }

    public static void requirePickup(int itemId, int draw, int scalar) {
        if (scalar < 0 || scalar > 100) {
            throw new ScenarioWorldException("INVALID_PICKER_SCALAR");
        }

        if (draw < 0) {
            throw new ScenarioWorldException("INVALID_PICKUP_DRAW");
        }

        int totalWeight = ITEMS.stream().mapToInt(item -> item.weight(scalar)).sum();
        int choice = draw % totalWeight;
        int selected = ITEMS.getLast().itemId();

        for (int index = 0; index < ITEMS.size() - 1; index++) {
            int weight = ITEMS.get(index).weight(scalar);

            if (choice < weight) {
                selected = ITEMS.get(index).itemId();
                break;
            }

            choice -= weight;
        }

        if (itemId != selected) {
            throw new ScenarioWorldException("INVALID_PICKUP_REWARD");
        }
    }
}

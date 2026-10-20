package taan.online.match.model;

import java.util.List;

public final class MatchNativeLootRules {
    private static final List<Integer> ITEMS = List.of(
        1,
        1005,
        1010,
        1015,
        1020,
        1025,
        1030,
        1035,
        1040,
        1045,
        1050,
        5,
        7
    );
    private static final List<Integer> WEIGHTS = List.of(30, 33, 30, 27, 24, 21, 18, 15, 12, 9, 6, 60, 15);
    private static final int TOTAL_WEIGHT = 300;

    private MatchNativeLootRules() {
    }

    public static void requirePickup(int itemId, int draw) {
        if (draw < 0) {
            throw new ScenarioWorldException("INVALID_PICKUP_DRAW");
        }

        int choice = draw % TOTAL_WEIGHT;
        int selected = ITEMS.getLast();

        for (int index = 0; index < ITEMS.size() - 1; index++) {
            int weight = WEIGHTS.get(index);

            if (choice < weight) {
                selected = ITEMS.get(index);
                break;
            }

            choice -= weight;
        }

        if (itemId != selected) {
            throw new ScenarioWorldException("INVALID_PICKUP_REWARD");
        }
    }
}

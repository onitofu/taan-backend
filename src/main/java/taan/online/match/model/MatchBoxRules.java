package taan.online.match.model;

import java.util.Map;

public final class MatchBoxRules {
    public static final int MAX_ACTIVE = 5;

    private MatchBoxRules() {
    }

    public static int activeCount(Map<Long, MatchItemBox> boxes) {
        return (int) boxes.values().stream().filter(MatchItemBox::active).count();
    }

    public static int budget(MatchBoxPolicy policy, Map<Long, MatchItemBox> boxes) {
        return policy == MatchBoxPolicy.ENABLED && activeCount(boxes) < MAX_ACTIVE ? 1 : 0;
    }

    public static void requireId(long id) {
        if (id >>> 56 != 5 || (id & 0x0000ffffff000000L) != 0) {
            throw new ScenarioWorldException("INVALID_BOX_ID");
        }
    }

    public static MatchItemBox create(
        Map<Long, MatchItemBox> boxes,
        MatchMapDefinition map,
        int issuedBudget,
        int creations,
        long id,
        int x,
        int y
    ) {
        requireId(id);

        if (boxes.containsKey(id)) {
            throw new ScenarioWorldException("DUPLICATE_BOX_ID");
        }

        if (x < 0 || x >= map.width() || y < 0 || y >= map.height()) {
            throw new ScenarioWorldException("INVALID_BOX_POSITION");
        }

        if (issuedBudget < 0 || issuedBudget > 1 || creations >= issuedBudget) {
            throw new ScenarioWorldException("BOX_BUDGET_EXCEEDED");
        }

        if (activeCount(boxes) >= MAX_ACTIVE) {
            throw new ScenarioWorldException("ACTIVE_BOX_LIMIT_EXCEEDED");
        }

        return new MatchItemBox(id, x, y, true);
    }
}

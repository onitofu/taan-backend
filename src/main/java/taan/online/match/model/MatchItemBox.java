package taan.online.match.model;

import java.util.OptionalInt;

public record MatchItemBox(long entityId, int x, int y, boolean active, OptionalInt boundItemId) {
    public static final int RADIUS = 35;
    public static final int RADIUS_SQUARED = 1225;

    public MatchItemBox(long entityId, int x, int y, boolean active) {
        this(entityId, x, y, active, OptionalInt.empty());
    }

    public MatchItemBox {
        MatchBoxRules.requireId(entityId);

        if (active && boundItemId.isPresent()) {
            throw new IllegalArgumentException("Active box cannot have a bound item");
        }
    }

    public MatchItemBox pickedUp(int itemId) {
        if (!active) {
            throw new ScenarioWorldException("INACTIVE_BOX");
        }

        return new MatchItemBox(entityId, x, y, false, OptionalInt.of(itemId));
    }
}

package taan.online.match.model;

public record MatchItemBox(long entityId, int x, int y, boolean active) {
    public static final int RADIUS = 35;
    public static final int RADIUS_SQUARED = 1225;

    public MatchItemBox {
        MatchBoxRules.requireId(entityId);
    }
}

package taan.online.match.model;

public record MapSpawnDefinition(
    int index,
    int rawX,
    int rawY,
    int settledX,
    int settledY,
    int side,
    boolean valid
) {
    public MapSpawnDefinition {
        if (index < 0 || rawX < 0 || rawY < 0 || settledX < 0 || settledY < 0
            || side < 0 || side > 2) {
            throw new IllegalArgumentException("Invalid map spawn definition");
        }
    }
}

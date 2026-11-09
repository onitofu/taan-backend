package taan.online.match.model;

public record GhostSegment(
    GhostPoint start,
    GhostPoint end,
    long startTick,
    long endTick,
    int facing,
    float easing,
    float frequencyX,
    float frequencyY
) {
    public int durationMillis() {
        return Math.toIntExact(endTick - startTick);
    }
}

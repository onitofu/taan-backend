package taan.online.match.model;

public record GhostMotionRequest(
    GhostPoint start,
    GhostPoint target,
    int facing,
    float speed,
    long startTick,
    long randomSeed
) {
}

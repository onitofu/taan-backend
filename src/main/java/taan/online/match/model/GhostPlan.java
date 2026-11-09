package taan.online.match.model;

import java.util.Optional;

public record GhostPlan(Optional<GhostSegment> segment, long randomSeed) {
}

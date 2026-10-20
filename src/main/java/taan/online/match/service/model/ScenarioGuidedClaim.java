package taan.online.match.service.model;

public record ScenarioGuidedClaim(
    ScenarioEventTime header,
    int sequence,
    int variant,
    int shotTick,
    int x,
    int y,
    int targetX,
    int targetY
) implements ScenarioClaim {
}

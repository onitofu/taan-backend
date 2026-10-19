package taan.online.match.service.model;

public record ScenarioProjectileClaim(
    ScenarioEventTime header,
    int sequence,
    int variant,
    int shotTick,
    float terrainFactor
) implements ScenarioClaim {
}

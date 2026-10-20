package taan.online.match.service.model;

public record ScenarioEatItemBoxClaim(
    ScenarioEventTime header,
    long entityId,
    int itemId,
    int opaque24
) implements ScenarioClaim {
}

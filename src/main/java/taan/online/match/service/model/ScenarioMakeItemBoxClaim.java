package taan.online.match.service.model;

public record ScenarioMakeItemBoxClaim(ScenarioEventTime header, long entityId, int x, int y) implements ScenarioClaim {
}

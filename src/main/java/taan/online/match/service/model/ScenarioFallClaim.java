package taan.online.match.service.model;

public record ScenarioFallClaim(ScenarioEventTime header, int startY, int endY) implements ScenarioClaim {
}

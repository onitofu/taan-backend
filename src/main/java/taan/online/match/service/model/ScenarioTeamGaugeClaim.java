package taan.online.match.service.model;

public record ScenarioTeamGaugeClaim(
    ScenarioEventTime header,
    int scalar10,
    int reason,
    int newGauge,
    int oldGauge
) implements ScenarioClaim {
}

package taan.online.match.service.model;

public record ScenarioDamageClaim(
    ScenarioEventTime header,
    int damage,
    int simulationHp,
    int energy1c,
    int energy1e
) implements ScenarioClaim {
}

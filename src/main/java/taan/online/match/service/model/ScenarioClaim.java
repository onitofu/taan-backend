package taan.online.match.service.model;

public sealed interface ScenarioClaim permits
    ScenarioFireClaim,
    ScenarioProjectileClaim,
    ScenarioDamageClaim,
    ScenarioFallClaim,
    ScenarioActorClaim,
    ScenarioMakeItemBoxClaim,
    ScenarioUnsupportedClaim {
    ScenarioEventTime header();
}

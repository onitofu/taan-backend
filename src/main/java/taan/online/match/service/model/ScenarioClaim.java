package taan.online.match.service.model;

public sealed interface ScenarioClaim permits
    ScenarioFireClaim,
    ScenarioProjectileClaim,
    ScenarioDamageClaim,
    ScenarioFallClaim,
    ScenarioActorClaim,
    ScenarioMakeItemBoxClaim,
    ScenarioEatItemBoxClaim,
    ScenarioUnsupportedClaim {
    ScenarioEventTime header();
}

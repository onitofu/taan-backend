package taan.online.match.service.model;

public sealed interface ScenarioClaim permits
    ScenarioFireClaim,
    ScenarioProjectileClaim,
    ScenarioGuidedClaim,
    ScenarioDamageClaim,
    ScenarioFallClaim,
    ScenarioActorClaim,
    ScenarioMakeItemBoxClaim,
    ScenarioEatItemBoxClaim,
    ScenarioUnsupportedClaim,
    ScenarioAnimationClaim,
    ScenarioTeamGaugeClaim {
    ScenarioEventTime header();
}

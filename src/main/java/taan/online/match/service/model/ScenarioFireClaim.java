package taan.online.match.service.model;

public record ScenarioFireClaim(
    ScenarioEventTime header,
    int sequence,
    int variant,
    int shotTick,
    int weaponId,
    int attackKind,
    float velocityX,
    float velocityY,
    float accelerationX,
    float accelerationY
) implements ScenarioClaim {
}

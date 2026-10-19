package taan.online.match.protocol.packet;

public record ScenarioWeaponFields(
    int sequence,
    int variant,
    ScenarioIntPoint position,
    int shotTick,
    int scalar24Bits,
    int scalar28
) {
}

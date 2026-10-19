package taan.online.match.protocol.packet;

public sealed interface ScenarioCommand permits
    ScenarioBaseCommand,
    ScenarioWeaponCommand,
    ScenarioFireCommand,
    ScenarioGuidedCommand,
    ScenarioNormalCommand,
    ScenarioDamageCommand,
    ScenarioMakeItemBoxCommand,
    ScenarioEatItemBoxCommand,
    ScenarioTeamGaugeCommand,
    ScenarioUseBonusCommand {
    ScenarioCommandHeader header();
}

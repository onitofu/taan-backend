package taan.online.match.protocol.packet;

public record ScenarioTeamGaugeCommand(
    ScenarioCommandHeader header,
    int reason,
    int newGauge,
    int oldGauge
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_TeamGauge";
    public static final int TYPE_ID = 0xa7650b3c;
}

package taan.online.match.protocol.packet;

public record ScenarioNormalCommand(
    ScenarioCommandHeader header,
    int scalar14,
    int scalar18
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_Normal";
    public static final int TYPE_ID = 0x87730a35;
}

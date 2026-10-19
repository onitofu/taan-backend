package taan.online.match.protocol.packet;

public record ScenarioBaseCommand(ScenarioCommandHeader header) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand";
    public static final int TYPE_ID = 0x48a3076d;
}

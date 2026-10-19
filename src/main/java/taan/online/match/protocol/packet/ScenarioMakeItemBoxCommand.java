package taan.online.match.protocol.packet;

public record ScenarioMakeItemBoxCommand(
    ScenarioCommandHeader header,
    long entityId,
    ScenarioIntPoint position
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_MakeItemBox";
    public static final int TYPE_ID = 0xbeb70c02;
}

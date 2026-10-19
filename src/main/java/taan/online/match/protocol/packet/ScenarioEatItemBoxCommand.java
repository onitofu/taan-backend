package taan.online.match.protocol.packet;

public record ScenarioEatItemBoxCommand(
    ScenarioCommandHeader header,
    long entityId,
    int itemId,
    int opaque24
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_EatItemBox";
    public static final int TYPE_ID = 0xb2a20b9e;
}

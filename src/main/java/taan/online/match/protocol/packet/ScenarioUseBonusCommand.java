package taan.online.match.protocol.packet;

public record ScenarioUseBonusCommand(ScenarioCommandHeader header, int itemId) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_UseBonusGameItem";
    public static final int TYPE_ID = 0x01d30e09;
}

package taan.online.match.protocol.packet;

public record ScenarioDamageCommand(
    ScenarioCommandHeader header,
    int damage,
    int simulationHp,
    int energy1c,
    int energy1e
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_Damage";
    public static final int TYPE_ID = 0x86be0a0b;
}

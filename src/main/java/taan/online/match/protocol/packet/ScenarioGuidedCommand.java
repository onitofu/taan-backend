package taan.online.match.protocol.packet;

public record ScenarioGuidedCommand(
    ScenarioCommandHeader header,
    ScenarioWeaponFields weapon,
    ScenarioIntPoint target
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_Guided";
    public static final int TYPE_ID = 0x87280a1e;
}

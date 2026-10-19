package taan.online.match.protocol.packet;

public record ScenarioWeaponCommand(
    ScenarioCommandHeader header,
    ScenarioWeaponFields weapon
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_Weapon";
    public static final int TYPE_ID = 0x875a0a36;
}

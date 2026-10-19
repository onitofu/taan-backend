package taan.online.match.protocol.packet;

public record ScenarioFireCommand(
    ScenarioCommandHeader header,
    ScenarioWeaponFields weapon,
    int weaponId,
    int attackKind,
    ScenarioFloatPoint velocity,
    ScenarioFloatPoint acceleration,
    int byte40,
    int word42
) implements ScenarioCommand {
    public static final String WIRE_NAME = "GameScenarioCommand_Fire";
    public static final int TYPE_ID = 0x733b0952;
}

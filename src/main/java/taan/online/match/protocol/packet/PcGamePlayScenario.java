package taan.online.match.protocol.packet;

public record PcGamePlayScenario(
    int facing,
    int shooterSlot,
    GameScenario scenario,
    int scalar18,
    int scalar1c,
    ScenarioTail tail
) {
    public static final String WIRE_NAME = "PcGamePlay_Scenario";
    public static final int TYPE_ID = 0x46de0756;
}

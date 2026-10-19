package taan.online.match.protocol.packet;

public record ScenarioWeaponEffect(int kind, float param1, float param2, int availableTurns, int availableOwnTurns) {
    public static final String WIRE_NAME = "WeaponEffect";
    public static final int TYPE_ID = 0x1e3c04b7;
}

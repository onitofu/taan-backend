package taan.online.match.protocol.packet;

public record PcGamePlayTurnStart(
    int activeSlot,
    int scalar11,
    int windRawTenths,
    int energyGrant,
    int boxAttemptBudget,
    long pickerBase,
    long pickerTeam0,
    long pickerTeam1,
    long pickerTeam2,
    int scalar50
) {
    public static final int TYPE_ID = 0x4f4c07d9;
}

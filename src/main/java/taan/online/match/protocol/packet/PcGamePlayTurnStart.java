package taan.online.match.protocol.packet;

import java.util.List;

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
    int scalar50,
    List<GhostMove> ghostMoves
) {
    public PcGamePlayTurnStart(
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
        this(
            activeSlot,
            scalar11,
            windRawTenths,
            energyGrant,
            boxAttemptBudget,
            pickerBase,
            pickerTeam0,
            pickerTeam1,
            pickerTeam2,
            scalar50,
            List.of()
        );
    }

    public PcGamePlayTurnStart {
        ghostMoves = List.copyOf(ghostMoves);
    }

    public static final int TYPE_ID = 0x4f4c07d9;
}

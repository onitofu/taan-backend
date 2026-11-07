package taan.online.match.protocol.packet;

import java.util.Optional;

public record PcGamePlayTurnEnd(int slotIndex, int scalar14, Optional<ScenarioTeamGaugeCommand> gauge) {
    public static final int TYPE_ID = 0x3fdb06e2;

    public PcGamePlayTurnEnd(int slotIndex, int scalar14) {
        this(slotIndex, scalar14, Optional.empty());
    }
}

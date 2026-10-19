package taan.online.match.protocol.packet;

import java.util.Optional;

public record PcGamePlayEatBonusItem(
    int slotIndex,
    ScenarioEatItemBoxCommand command,
    Optional<ScenarioTeamGaugeCommand> gauge
) {
    public static final String WIRE_NAME = "PcGamePlay_EatBonusItem";
    public static final int TYPE_ID = 0x66e908d2;
}

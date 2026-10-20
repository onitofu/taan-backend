package taan.online.match.protocol.packet;

import java.util.Optional;

public record PcGamePlayEatItemBox(
    int slotIndex,
    ScenarioEatItemBoxCommand command,
    Optional<ScenarioTeamGaugeCommand> gauge
) {
    public static final String WIRE_NAME = "PcGamePlay_EatItemBox";
    public static final int TYPE_ID = 0x557107f4;
}

package taan.online.match.protocol.codec;

import java.util.Optional;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;

record EatItemPayload(
    int slotIndex,
    ScenarioEatItemBoxCommand command,
    Optional<ScenarioTeamGaugeCommand> gauge
) {
}

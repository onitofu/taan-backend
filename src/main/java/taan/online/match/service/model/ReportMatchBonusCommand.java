package taan.online.match.service.model;

import java.util.Optional;

public record ReportMatchBonusCommand(
    MatchCommandContext context,
    int slotIndex,
    int commandSlot,
    Optional<ScenarioTeamGaugeClaim> gauge,
    long entityId,
    int itemId,
    int tick,
    int scalar10,
    int opaque24
) {
}

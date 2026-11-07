package taan.online.match.service.model;

import java.util.Optional;

public record ReportMatchPickupCommand(
    MatchCommandContext context,
    int slotIndex,
    int commandSlot,
    Optional<ScenarioTeamGaugeClaim> gauge,
    long entityId,
    int itemId
) {
}

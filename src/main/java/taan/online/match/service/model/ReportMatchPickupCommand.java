package taan.online.match.service.model;

import java.util.Optional;

public record ReportMatchPickupCommand(
    MatchCommandContext context,
    int slotIndex,
    int commandSlot,
    Optional<ScenarioTeamGaugeClaim> gauge,
    long entityId,
    int itemId,
    int tick,
    int scalar10,
    int opaque24,
    long turnRevision
) {
    public ReportMatchPickupCommand(
        MatchCommandContext context,
        int slotIndex,
        int commandSlot,
        Optional<ScenarioTeamGaugeClaim> gauge,
        long entityId,
        int itemId
    ) {
        this(context, slotIndex, commandSlot, gauge, entityId, itemId, 0, 0, 0, -1);
    }
}

package taan.online.match.service.model;

import java.util.OptionalInt;

public record ReportMatchBonusCommand(
    MatchCommandContext context,
    int slotIndex,
    int commandSlot,
    OptionalInt gaugeSlot,
    long entityId,
    int itemId,
    int tick,
    int scalar10,
    int opaque24
) {
}

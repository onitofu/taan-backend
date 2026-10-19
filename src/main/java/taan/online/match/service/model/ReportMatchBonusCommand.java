package taan.online.match.service.model;

import java.util.OptionalInt;

public record ReportMatchBonusCommand(
    MatchCommandContext context,
    int slotIndex,
    int commandSlot,
    OptionalInt gaugeSlot,
    long entityId
) {
}

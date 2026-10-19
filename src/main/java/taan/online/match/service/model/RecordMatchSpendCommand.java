package taan.online.match.service.model;

import java.util.List;

public record RecordMatchSpendCommand(
    MatchCommandContext context,
    long turnRevision,
    long phaseRevision,
    int slot,
    int teamClear11,
    int teamClear12,
    List<SpentMatchItem> items
) {
    public RecordMatchSpendCommand {
        items = List.copyOf(items);
    }
}

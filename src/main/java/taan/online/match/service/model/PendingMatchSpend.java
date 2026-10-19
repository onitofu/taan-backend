package taan.online.match.service.model;

import java.util.List;

public record PendingMatchSpend(
    MatchCommandContext context,
    long turnRevision,
    int slot,
    int teamClear11,
    int teamClear12,
    List<SpentMatchItem> items
) {
    public PendingMatchSpend {
        items = List.copyOf(items);
    }
}

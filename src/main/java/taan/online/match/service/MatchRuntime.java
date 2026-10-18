package taan.online.match.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import taan.online.match.model.Match;
import taan.online.room.service.RoomMatchCallbacks;

final class MatchRuntime {
    final Match match;
    final Map<UUID, MatchReportBudget> reportBudgets = new HashMap<>();
    RoomMatchCallbacks callbacks;
    List<MatchSubscription> recipients = List.of();
    long publishedRevision = -1;

    MatchRuntime(Match match) {
        this.match = match;
    }
}

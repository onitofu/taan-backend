package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.room.service.model.RoomMatchContext;

public final class ReportMatchGhostAction {
    private static final EventLogger LOG = EventLogger.forClass(ReportMatchGhostAction.class);
    private final MatchRegistry matches;

    public ReportMatchGhostAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Void> execute(UUID id, RoomMatchContext context, int slot) {
        if (context == null || !context.membership().sessionId().equals(id)) {
            ignored(id, null, slot, "STALE_CONTEXT");
            return CompletableFuture.completedFuture(null);
        }

        var sender = MatchCommandContext.from(context);
        var current = matches.context(id);
        var snapshot = matches.current(context.token()).orElse(null);
        boolean ending = sender.equals(current) && snapshot != null
            && snapshot.participants().stream().anyMatch(p -> p.sessionId().equals(id) && p.slotIndex() == slot)
            && snapshot.actors().get(id).dead();

        ignored(id, sender, slot, ending ? "SPECTATOR" : "STALE_OR_INAPPLICABLE");
        return CompletableFuture.completedFuture(null);
    }

    private static void ignored(UUID id, MatchCommandContext context, int slot, String cause) {
        LOG.log(
            MatchLogEvent.GHOST_REQUEST_IGNORED,
            sessionId(id),
            matchId(context == null ? null : context.matchId().value()),
            admissionId(context == null ? null : context.admissionId()),
            slotIndex(slot),
            reason(cause)
        );
    }
}

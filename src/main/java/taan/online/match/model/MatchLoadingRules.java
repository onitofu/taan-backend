package taan.online.match.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import taan.online.room.model.RoomTeam;

final class MatchLoadingRules {
    private MatchLoadingRules() {
    }

    static String validate(Match match, UUID session, int slot, int x, int y) {
        var actor = match.participants().stream().filter(p -> p.sessionId().equals(session)).findFirst().orElse(null);

        if (actor == null || actor.slotIndex() != slot) {
            return "FOREIGN_SLOT";
        }

        var accepted = match.loadedSessions().contains(session) ? match.actors().get(session) : null;

        if (accepted != null) {
            return accepted.x() == x && accepted.y() == y ? "UNCHANGED" : "CONFLICTING_POSITION";
        }

        if (match.phase() != MatchPhase.LOADING) {
            return "INVALID_PHASE";
        }

        int side = actor.team() == RoomTeam.BLUE ? 1 : 2;
        boolean valid = match.definitions().map().spawns().stream()
            .anyMatch(p -> p.valid() && (p.side() == 0 || p.side() == side) && p.settledX() == x && p.settledY() == y);
        return valid ? "ACCEPTED" : "INVALID_POSITION";
    }

    static boolean delivered(Match match, Set<UUID> deliveries) {
        return match.participants().stream().filter(p -> !match.resolution().exits().containsKey(p.sessionId()))
            .allMatch(p -> deliveries.contains(p.sessionId()));
    }

    static boolean ready(Match match, Set<UUID> deliveries) {
        return delivered(match, deliveries) && delivered(match, match.loadedSessions());
    }

    static MatchTurn turn(Match match, long now) {
        var previous = match.turn();
        var slots = match.participants().stream().filter(p -> !match.resolution().exits().containsKey(p.sessionId()))
            .map(MatchParticipant::slotIndex).collect(Collectors.toSet());
        List<Integer> order = previous.order().stream().filter(slots::contains).toList();
        return new MatchTurn(
            order.getFirst(),
            previous.windRaw(),
            0,
            previous.boxAttemptBudget(),
            previous.pickerBase(),
            order,
            previous.revision(),
            previous.issuedAtNanos(),
            previous.round(),
            slots,
            previous.randomState()
        ).issuedAt(now);
    }
}

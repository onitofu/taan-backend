package taan.online.match.model;

import java.util.ArrayList;
import java.util.List;

public record MatchTeamItemState(
    List<Integer> team,
    List<Integer> ghostTeam,
    List<MatchSharedItemReceipt> receipts
) {
    private static final MatchTeamItemState EMPTY_STATE = new MatchTeamItemState(List.of(), List.of());

    public MatchTeamItemState(List<Integer> team, List<Integer> ghostTeam) {
        this(team, ghostTeam, List.of());
    }

    public MatchTeamItemState {
        team = List.copyOf(team);
        ghostTeam = List.copyOf(ghostTeam);
        receipts = List.copyOf(receipts);
    }

    public static MatchTeamItemState empty() {
        return EMPTY_STATE;
    }

    public MatchTeamItemState activated(MatchSharedItemReceipt activation) {
        var route = activation.route();
        int item = activation.receipt().itemId();
        var sources = new ArrayList<>(receipts);
        sources.add(activation);
        var selected = new ArrayList<>(route == MatchItemRoute.TEAM ? team : ghostTeam);
        selected.add(item);

        if (selected.size() > MatchAttackSelection.MAX_ACTIVATIONS) {
            throw new ScenarioWorldException("ACTIVE_ITEM_LIMIT");
        }

        return route == MatchItemRoute.TEAM ? new MatchTeamItemState(selected, ghostTeam, sources)
            : new MatchTeamItemState(team, selected, sources);
    }

    public MatchTeamItemState cleared(boolean clearTeam, boolean clearGhost) {
        var retained = receipts.stream().filter(
            value -> value.route() == MatchItemRoute.TEAM ? !clearTeam : !clearGhost
        ).toList();
        return new MatchTeamItemState(clearTeam ? List.of() : team, clearGhost ? List.of() : ghostTeam, retained);
    }
}

package taan.online;

import java.util.Optional;
import taan.online.match.model.Match;
import taan.online.match.model.MatchCompletion;
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.ScenarioLootDraft;

public final class MatchStateTestAccess {
    private MatchStateTestAccess() {
    }

    public static boolean commit(Match match, long world, long turn, MatchWorldState prepared) {
        return commit(match, world, turn, prepared, Optional.empty());
    }

    public static boolean commit(
        Match match,
        long world,
        long turn,
        MatchWorldState prepared,
        Optional<MatchCompletion> pending
    ) {
        var loot = match.loot();
        return match.commitScenario(world, turn, prepared, pending, loot, new ScenarioLootDraft(loot).finish(prepared));
    }
}

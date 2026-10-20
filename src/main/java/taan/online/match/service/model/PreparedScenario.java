package taan.online.match.service.model;

import taan.online.match.model.MatchLootState;
import taan.online.match.model.MatchWorldState;

public record PreparedScenario(
    AcceptedScenario scenario,
    long baseWorldRevision,
    MatchWorldState world,
    MatchLootState baseLoot,
    MatchLootState loot,
    int changedCells,
    int deaths
) {
}

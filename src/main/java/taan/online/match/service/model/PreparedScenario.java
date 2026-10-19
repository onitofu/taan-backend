package taan.online.match.service.model;

import taan.online.match.model.MatchWorldState;

public record PreparedScenario(
    AcceptedScenario scenario,
    long baseWorldRevision,
    MatchWorldState world,
    int changedCells,
    int deaths
) {
}

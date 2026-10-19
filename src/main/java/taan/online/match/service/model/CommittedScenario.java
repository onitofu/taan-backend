package taan.online.match.service.model;

public record CommittedScenario(AcceptedScenario scenario, MatchSnapshot snapshot, int changedCells, int deaths) {
}

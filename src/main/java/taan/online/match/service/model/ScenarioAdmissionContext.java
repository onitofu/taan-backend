package taan.online.match.service.model;

import java.util.Optional;

public record ScenarioAdmissionContext(
    MatchSnapshot match,
    MatchCommandContext sender,
    long expectedTurnRevision,
    Optional<AcceptedScenario> previous
) {
}

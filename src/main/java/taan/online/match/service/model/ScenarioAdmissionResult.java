package taan.online.match.service.model;

import java.util.Optional;

public record ScenarioAdmissionResult(
    ScenarioAdmissionOutcome outcome,
    String reason,
    Optional<AcceptedScenario> accepted
) {
}

package taan.online.match.service.model;

import java.util.Optional;

public record ScenarioApplicationResult(
    ScenarioApplicationOutcome outcome,
    String reason,
    Optional<CommittedScenario> committed
) {
    public static ScenarioApplicationResult ignored(String reason) {
        return new ScenarioApplicationResult(ScenarioApplicationOutcome.IGNORED, reason, Optional.empty());
    }
}

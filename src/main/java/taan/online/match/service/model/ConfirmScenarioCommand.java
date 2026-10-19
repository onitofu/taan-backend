package taan.online.match.service.model;

import java.util.Optional;

public record ConfirmScenarioCommand(
    MatchCommandContext context,
    long turnRevision,
    long worldRevision,
    Optional<String> fingerprint,
    int scenarioSlot
) {
}

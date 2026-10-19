package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ScenarioApplicationResult;

public final class ApplyScenarioAction {
    private final MatchRegistry matches;

    public ApplyScenarioAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<ScenarioApplicationResult> execute(ApplyScenarioCommand command) {
        return matches.applyScenario(command);
    }
}

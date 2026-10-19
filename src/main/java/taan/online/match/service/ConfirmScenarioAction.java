package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ConfirmScenarioCommand;

public final class ConfirmScenarioAction {
    private final MatchRegistry matches;

    public ConfirmScenarioAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(ConfirmScenarioCommand command) {
        return matches.confirmScenario(command);
    }
}

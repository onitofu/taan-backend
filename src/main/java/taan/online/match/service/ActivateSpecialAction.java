package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ActivateSpecialCommand;

public final class ActivateSpecialAction {
    private final MatchRegistry matches;

    public ActivateSpecialAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(ActivateSpecialCommand command) {
        return matches.activateSpecial(command);
    }
}

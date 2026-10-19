package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.PassMatchTurnCommand;

public final class PassMatchTurnAction {
    private final MatchRegistry matches;

    public PassMatchTurnAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(PassMatchTurnCommand command) {
        return matches.passTurn(command);
    }
}

package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.MoveMatchActorCommand;

public final class MoveMatchActorAction {
    private final MatchRegistry matches;

    public MoveMatchActorAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(MoveMatchActorCommand command) {
        return matches.move(command);
    }
}

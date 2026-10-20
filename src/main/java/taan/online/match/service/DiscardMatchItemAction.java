package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.DiscardMatchItemCommand;

public final class DiscardMatchItemAction {
    private final MatchRegistry matches;

    public DiscardMatchItemAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(DiscardMatchItemCommand command) {
        return matches.discardItem(command);
    }
}

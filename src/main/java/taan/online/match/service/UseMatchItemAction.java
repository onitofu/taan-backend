package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.UseMatchItemCommand;

public final class UseMatchItemAction {
    private final MatchRegistry matches;

    public UseMatchItemAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(UseMatchItemCommand command) {
        return matches.useItem(command);
    }
}

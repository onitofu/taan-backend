package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.RecordMatchSpendCommand;

public final class RecordMatchSpendAction {
    private final MatchRegistry matches;

    public RecordMatchSpendAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(RecordMatchSpendCommand command) {
        return matches.recordSpend(command);
    }
}

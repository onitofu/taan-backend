package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.SelectMatchRewardCommand;

public final class SelectMatchRewardAction {
    private final MatchRegistry matches;

    public SelectMatchRewardAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Void> execute(SelectMatchRewardCommand command) {
        return matches.selectReward(command);
    }
}

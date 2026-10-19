package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ReportMatchBonusCommand;

public final class ReportMatchBonusAction {
    private final MatchRegistry matches;

    public ReportMatchBonusAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(ReportMatchBonusCommand command) {
        return matches.reportBonus(command);
    }
}

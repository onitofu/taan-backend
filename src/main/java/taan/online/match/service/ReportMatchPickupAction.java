package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ReportMatchPickupCommand;

public final class ReportMatchPickupAction {
    private final MatchRegistry matches;

    public ReportMatchPickupAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(ReportMatchPickupCommand command) {
        return matches.reportPickup(command);
    }
}

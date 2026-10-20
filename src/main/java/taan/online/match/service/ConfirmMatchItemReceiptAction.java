package taan.online.match.service;

import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;

public final class ConfirmMatchItemReceiptAction {
    private final MatchRegistry matches;

    public ConfirmMatchItemReceiptAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(ConfirmMatchItemReceiptCommand command) {
        return matches.confirmItemReceipt(command);
    }
}

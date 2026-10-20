package taan.online.match.service;

import java.util.UUID;
import taan.online.match.service.model.PendingMatchSpend;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchOutcome;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.MatchTurnTransition;

public interface MatchOutput {
    CompletionStage<Void> start(MatchSnapshot snapshot);

    CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient);

    CompletionStage<Void> nextTurn(MatchTurnTransition transition);

    CompletionStage<Void> movement(MatchMovementNotice notice);

    CompletionStage<Void> stopTurn(int slot);

    CompletionStage<Void> special(int slot, int team);

    CompletionStage<Void> spend(PendingMatchSpend spend);

    CompletionStage<Void> bonus(MatchBonusDelivery bonus);

    CompletionStage<Void> discard(DiscardMatchItemCommand discard);

    CompletionStage<Void> scenario(CommittedScenario scenario);

    CompletionStage<Void> end(MatchOutcome outcome);

    void close(String reason);
}

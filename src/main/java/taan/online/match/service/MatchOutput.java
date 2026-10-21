package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.MatchEndResult;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchRewardSelectionNotice;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchTurnTransition;
import taan.online.match.service.model.PendingMatchSpend;
import taan.online.match.service.model.UseMatchItemCommand;

public interface MatchOutput {
    CompletionStage<Void> start(MatchSnapshot snapshot);

    CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient);

    CompletionStage<Void> nextTurn(MatchTurnTransition transition);

    CompletionStage<Void> movement(MatchMovementNotice notice);

    CompletionStage<Void> stopTurn(int slot);

    CompletionStage<Void> useItem(UseMatchItemCommand command);

    CompletionStage<Void> spend(PendingMatchSpend spend);

    CompletionStage<Void> bonus(MatchBonusDelivery bonus);

    CompletionStage<Void> discard(DiscardMatchItemCommand discard);

    CompletionStage<Void> scenario(CommittedScenario scenario);

    CompletionStage<Void> end(MatchEndResult result);

    CompletionStage<Void> selectReward(MatchRewardSelectionNotice notice);

    void close(String reason);
}

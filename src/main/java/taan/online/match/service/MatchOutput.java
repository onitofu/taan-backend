package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchOutcome;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.CommittedScenario;

public interface MatchOutput {
    CompletionStage<Void> start(MatchSnapshot snapshot);

    CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient);

    CompletionStage<Void> scenario(CommittedScenario scenario);

    CompletionStage<Void> end(MatchOutcome outcome);

    void close(String reason);
}

package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.MatchSnapshot;

public interface MatchOutput {
    CompletionStage<Void> start(MatchSnapshot snapshot);

    CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient);

    CompletionStage<Void> endPreview();

    void close(String reason);
}

package taan.online.match.service;

import java.util.concurrent.CompletionStage;

public interface MatchOutput {
    CompletionStage<Void> endPreview();

    void close(String reason);
}

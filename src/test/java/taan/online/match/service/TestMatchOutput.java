package taan.online.match.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class TestMatchOutput implements MatchOutput {
    public int ends;
    public final List<String> closed = new ArrayList<>();
    public CompletableFuture<Void> delivery = CompletableFuture.completedFuture(null);
    public RuntimeException failure;
    public Runnable onEnd = () -> { };

    @Override
    public CompletionStage<Void> endPreview() {
        ends++;
        onEnd.run();

        if (failure != null) {
            throw failure;
        }

        return delivery;
    }

    @Override
    public void close(String reason) {
        closed.add(reason);
    }
}

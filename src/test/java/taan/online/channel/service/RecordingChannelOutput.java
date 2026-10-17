package taan.online.channel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelParticipant;
import taan.online.character.model.CharacterId;

final class RecordingChannelOutput implements ChannelPresenceOutput {
    final List<List<ChannelParticipant>> snapshots = new ArrayList<>();
    final List<ChannelParticipant> added = new ArrayList<>();
    final List<CharacterId> removed = new ArrayList<>();
    Runnable onInitialize = () -> { };
    int population;
    int unavailable;
    int left;

    @Override
    public CompletionStage<Void> initialize(int count, List<ChannelParticipant> users) {
        population = count;
        snapshots.add(List.copyOf(users));
        onInitialize.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> update(List<ChannelParticipant> users, List<CharacterId> ids) {
        added.addAll(users);
        removed.addAll(ids);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unavailable() {
        unavailable++;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> left() {
        left++;
        return CompletableFuture.completedFuture(null);
    }
}

package taan.online.channel.service;

import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelParticipant;
import taan.online.character.model.CharacterId;

public interface ChannelPresenceOutput {
    CompletionStage<Void> initialize(int population, List<ChannelParticipant> users);

    CompletionStage<Void> update(List<ChannelParticipant> added, List<CharacterId> removed);

    CompletionStage<Void> roomChanged(int roomId);

    CompletionStage<Void> roomsReplaced();

    void close(String reason);

    CompletionStage<Void> unavailable();

    CompletionStage<Void> left();
}

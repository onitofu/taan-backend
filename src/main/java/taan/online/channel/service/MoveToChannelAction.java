package taan.online.channel.service;

import java.util.UUID;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.channel.service.model.MoveToResult;

public final class MoveToChannelAction {
    private final ChannelRegistry channels;

    public MoveToChannelAction(ChannelRegistry channels) {
        this.channels = channels;
    }

    public MoveToResult execute(UUID sessionId, MoveToCommand command) {
        return channels.move(sessionId, command);
    }

    public void close(UUID id, String reason) {
        channels.release(id, reason);
    }
}

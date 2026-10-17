package taan.online.channel.controller;

import java.util.UUID;
import taan.online.channel.protocol.codec.MoveToRequestCodec;
import taan.online.channel.protocol.packet.ChannelInfo;
import taan.online.channel.protocol.packet.PrChannelMoveTo;
import taan.online.channel.service.MoveToChannelAction;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.channel.service.model.MoveToOutcome;

public final class MoveToChannelPacketHandler {
    private final MoveToChannelAction action;
    private final SelectedCharacterAccess sessions;
    private final int port;

    public MoveToChannelPacketHandler(MoveToChannelAction action, SelectedCharacterAccess sessions, int port) {
        this.action = action;
        this.sessions = sessions;
        this.port = port;
    }

    public MoveToHandlingResult handle(UUID id, byte[] root) {
        if (!sessions.hasSelectedCharacter(id)) {
            return new MoveToHandlingResult(ChannelHandlingOutcome.IGNORED, null);
        }

        var request = new MoveToRequestCodec().decode(root);
        var result = action.execute(
            id,
            new MoveToCommand(
                request.mode(),
                request.channelId(),
                request.followArgument(),
                request.clientRevision()
            )
        );

        if (result.outcome() == MoveToOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
            return new MoveToHandlingResult(ChannelHandlingOutcome.IGNORED, null);
        }

        int code = switch (result.outcome()) {
            case MOVED -> 0;
            case FULL -> 100;
            case LEVEL_TOO_HIGH -> 101;
            case LEVEL_TOO_LOW -> 102;
            case DISABLED -> 103;
            default -> 255;
        };
        var snapshot = result.channel();
        ChannelInfo info = null;

        if (snapshot != null) {
            var channel = snapshot.definition();
            info = new ChannelInfo(
                channel.name(),
                channel.id(),
                port,
                snapshot.fill(),
                channel.minLevel(),
                channel.maxLevel(),
                !channel.enabled()
            );
        }

        return new MoveToHandlingResult(
            code == 0 ? ChannelHandlingOutcome.MOVED : ChannelHandlingOutcome.REJECTED,
            new PrChannelMoveTo(code, result.channelId(), port, info)
        );
    }

    public void close(UUID id, String reason) {
        action.close(id, reason);
    }
}

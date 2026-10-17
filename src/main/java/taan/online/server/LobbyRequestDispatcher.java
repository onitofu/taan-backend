package taan.online.server;

import java.util.UUID;
import taan.online.channel.controller.MoveToChannelPacketHandler;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.channel.protocol.codec.MoveToResponseCodec;
import taan.online.channel.protocol.packet.PqChannelMoveTo;
import taan.online.lobby.controller.LobbyPacketHandler;
import taan.online.lobby.logging.LobbyLogEvent;
import taan.online.lobby.protocol.packet.PqLobbyEnter;
import taan.online.lobby.protocol.packet.PqLobbyLeave;
import taan.online.logging.LogEvent;
import taan.online.protocol.PacketOutput;

public final class LobbyRequestDispatcher {
    private final MoveToChannelPacketHandler move;
    private final LobbyPacketHandler lobby;

    public LobbyRequestDispatcher(MoveToChannelPacketHandler move, LobbyPacketHandler lobby) {
        this.move = move;
        this.lobby = lobby;
    }

    RequestCompletion dispatch(int type, UUID sessionId, byte[] root, PacketOutput output) {
        if (type == PqChannelMoveTo.TYPE_ID) {
            var result = move.handle(sessionId, root);
            var event = switch (result.outcome()) {
                case MOVED -> ProtocolEvent.CHANNEL_MOVED;
                case REJECTED -> ProtocolEvent.CHANNEL_MOVE_REJECTED;
                case IGNORED -> ProtocolEvent.CHANNEL_REQUEST_IGNORED;
            };
            return RequestCompletion.reply(
                output,
                event,
                result.response() == null ? null : new MoveToResponseCodec().encode(result.response())
            );
        }

        var result = lobby.handle(sessionId, root, type == PqLobbyEnter.TYPE_ID, output);
        var event = switch (result.outcome()) {
            case ENTERED -> ProtocolEvent.LOBBY_ENTERED;
            case LEFT_MEMBER -> ProtocolEvent.LOBBY_LEFT_MEMBER;
            case LEFT_UNBOUND -> ProtocolEvent.LOBBY_LEFT_UNBOUND;
            case UNAVAILABLE -> ProtocolEvent.LOBBY_UNAVAILABLE;
            case IGNORED -> ProtocolEvent.CHANNEL_REQUEST_IGNORED;
        };
        return new RequestCompletion(event, result.delivery());
    }

    void close(UUID id, String reason) {
        move.close(id, reason);
    }

    static boolean accepts(int type) {
        return type == PqChannelMoveTo.TYPE_ID || type == PqLobbyEnter.TYPE_ID || type == PqLobbyLeave.TYPE_ID;
    }

    static LogEvent failureEvent(int type) {
        return type == PqChannelMoveTo.TYPE_ID ? ChannelLogEvent.MOVE_FAILED : LobbyLogEvent.FAILED;
    }
}

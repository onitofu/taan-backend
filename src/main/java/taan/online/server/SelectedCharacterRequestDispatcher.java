package taan.online.server;

import java.util.UUID;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.channel.protocol.codec.ChannelListResponseCodec;
import taan.online.channel.protocol.packet.PqGetChannelList;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.codec.SelectCharacterResponseCodec;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.logging.LogEvent;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.protocol.codec.UpdateCashCodec;
import taan.online.session.protocol.packet.PcLogging;
import taan.online.session.protocol.packet.PcUpdateCash;

public final class SelectedCharacterRequestDispatcher {
    private final SelectCharacterPacketHandler select;
    private final GetChannelListPacketHandler channels;
    private final CashRefreshPacketHandler cash;
    private final ClientLoggingPacketHandler logging;

    public SelectedCharacterRequestDispatcher(
        SelectCharacterPacketHandler select,
        GetChannelListPacketHandler channels,
        CashRefreshPacketHandler cash,
        ClientLoggingPacketHandler logging
    ) {
        this.select = select;
        this.channels = channels;
        this.cash = cash;
        this.logging = logging;
    }

    RequestCompletion dispatch(int type, UUID sessionId, byte[] root) {
        return switch (type) {
            case PqSelectCharacter.TYPE_ID -> {
                var result = select.handle(sessionId, root);
                yield switch (result.outcome()) {
                    case SELECTED -> new RequestCompletion(
                        ProtocolEvent.CHARACTER_SELECTED,
                        new SelectCharacterResponseCodec().encode(result.response())
                    );
                    case REJECTED -> new RequestCompletion(ProtocolEvent.CHARACTER_SELECTION_REJECTED, null);
                    case IGNORED -> new RequestCompletion(ProtocolEvent.CHARACTER_REQUEST_IGNORED, null);
                };
            }
            case PqGetChannelList.TYPE_ID -> channels.handle(sessionId, root).map(
                reply -> new RequestCompletion(
                    ProtocolEvent.SELECTED_REQUEST_COMPLETED,
                    new ChannelListResponseCodec().encode(reply)
                )
            ).orElseGet(() -> new RequestCompletion(ProtocolEvent.SELECTED_REQUEST_IGNORED, null));
            case PcUpdateCash.TYPE_ID -> cash.handle(sessionId, root).map(
                reply -> new RequestCompletion(
                    ProtocolEvent.SELECTED_REQUEST_COMPLETED,
                    new UpdateCashCodec().encode(reply)
                )
            ).orElseGet(() -> new RequestCompletion(ProtocolEvent.SELECTED_REQUEST_IGNORED, null));
            default -> new RequestCompletion(
                logging.handle(sessionId, root)
                    ? ProtocolEvent.SELECTED_REQUEST_COMPLETED : ProtocolEvent.SELECTED_REQUEST_IGNORED,
                null
            );
        };
    }

    static boolean accepts(int type) {
        return type == PqSelectCharacter.TYPE_ID || type == PqGetChannelList.TYPE_ID
            || type == PcUpdateCash.TYPE_ID || type == PcLogging.TYPE_ID;
    }

    static LogEvent failureEvent(int type) {
        return type == PqSelectCharacter.TYPE_ID ? CharacterLogEvent.SELECTION_FAILED : ChannelLogEvent.LIST_FAILED;
    }
}

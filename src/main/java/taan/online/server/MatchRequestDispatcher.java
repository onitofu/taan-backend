package taan.online.server;

import java.util.UUID;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.controller.MatchRequestContext;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.protocol.PacketOutput;

public final class MatchRequestDispatcher {
    private final MatchPacketHandler handler;

    public MatchRequestDispatcher(MatchPacketHandler handler) {
        this.handler = handler;
    }

    void open(UUID id, PacketOutput output) {
        handler.open(id, output);
    }

    void close(UUID id) {
        handler.close(id);
    }

    MatchRequestContext capture(UUID id, int type) {
        return accepts(type) ? handler.capture(id) : null;
    }

    RequestCompletion dispatch(UUID id, PendingRequest request) {
        return new RequestCompletion(
            ProtocolEvent.MATCH_REQUEST_COMPLETED,
            handler.handle(id, request.type(), request.root(), request.matchContext())
        );
    }

    static boolean accepts(int type) {
        return type == PcGamePlayGiveUp.TYPE_ID || type == PqResultRandSelect.TYPE_ID
            || type == PqResultSelectCard.TYPE_ID;
    }
}

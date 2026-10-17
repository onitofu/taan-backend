package taan.online.session.controller;

import java.util.Optional;
import java.util.UUID;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.model.SessionId;
import taan.online.session.protocol.codec.UpdateCashCodec;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.service.SessionRegistry;

public final class CashRefreshPacketHandler {
    private final SessionRegistry sessions;

    public CashRefreshPacketHandler(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public Optional<PcUpdateCash> handle(UUID sessionId, byte[] root) {
        if (sessions.selectedCharacter(new SessionId(sessionId)).isEmpty()) {
            return Optional.empty();
        }

        var request = new UpdateCashCodec().decode(root);

        if (request.localSlot() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_LOCAL_ACCOUNT");
        }

        return Optional.of(new PcUpdateCash(0, 0));
    }
}

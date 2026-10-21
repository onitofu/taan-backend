package taan.online.session.controller;

import java.util.Optional;
import java.util.UUID;
import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.model.SessionId;
import taan.online.session.protocol.codec.UpdateCashCodec;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.service.RefreshBalancesAction;
import taan.online.session.service.SessionRegistry;

public final class CashRefreshPacketHandler {
    private final SessionRegistry sessions;
    private final RefreshBalancesAction refresh;

    public CashRefreshPacketHandler(SessionRegistry sessions, RefreshBalancesAction refresh) {
        this.sessions = sessions;
        this.refresh = refresh;
    }

    public Optional<CashRefreshResult> handle(UUID sessionId, byte[] root) {
        if (sessions.selectedCharacter(new SessionId(sessionId)).isEmpty()) {
            return Optional.empty();
        }

        var request = new UpdateCashCodec().decode(root);

        if (request.localSlot() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_LOCAL_ACCOUNT");
        }

        return refresh.execute(sessionId).map(
            snapshot -> new CashRefreshResult(
                new PcUpdateCash(snapshot.cash(), 0),
                new PcUpdateMoney(0, 0, snapshot.gold(), 0, 0)
            )
        );
    }
}

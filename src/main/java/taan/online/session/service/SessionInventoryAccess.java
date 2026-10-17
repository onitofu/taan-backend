package taan.online.session.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.inventory.service.SelectedInventoryAccess;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.session.model.SessionId;

public final class SessionInventoryAccess implements SelectedInventoryAccess {
    private final SessionRegistry sessions;

    public SessionInventoryAccess(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<InventoryActor> selected(UUID sessionId) {
        return sessions.inventoryActor(new SessionId(sessionId));
    }
}

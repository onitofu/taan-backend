package taan.online.inventory.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.inventory.service.model.InventoryActor;

public interface SelectedInventoryAccess {
    Optional<InventoryActor> selected(UUID sessionId);
}

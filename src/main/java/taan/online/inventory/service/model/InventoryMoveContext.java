package taan.online.inventory.service.model;

import java.util.Objects;
import java.util.UUID;

public record InventoryMoveContext(InventoryActor actor, UUID roomAdmissionId, boolean equipmentChangesAllowed) {
    public InventoryMoveContext {
        Objects.requireNonNull(actor);
    }
}

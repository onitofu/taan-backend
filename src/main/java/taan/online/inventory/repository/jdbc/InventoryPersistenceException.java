package taan.online.inventory.repository.jdbc;

import taan.online.logging.SafeLogException;

public final class InventoryPersistenceException extends RuntimeException {
    public InventoryPersistenceException(Throwable failure) {
        super("Inventory persistence failed", new SafeLogException(failure));
    }
}

package taan.online.inventory.repository;

import taan.online.account.model.AccountId;

public interface InventoryRepository {
    InventoryTransaction beginTransaction(AccountId owner, long characterId);
}

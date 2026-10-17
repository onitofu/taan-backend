package taan.online.inventory.service.model;

import java.util.UUID;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

public record InventoryActor(UUID sessionId, AccountId accountId, long characterId, AccountGender gender) {
}

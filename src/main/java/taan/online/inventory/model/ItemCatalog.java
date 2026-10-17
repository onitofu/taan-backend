package taan.online.inventory.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import taan.online.account.model.AccountGender;

public final class ItemCatalog {
    private Map<Long, ItemDefinition> definitions;

    public synchronized void initialize(List<ItemDefinition> values) {
        if (definitions != null) {
            throw new IllegalStateException("Item definitions already loaded");
        }

        var index = new HashMap<Long, ItemDefinition>();

        for (var definition : values) {
            if (index.put(definition.itemId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate item definition");
            }
        }

        definitions = Map.copyOf(index);
    }

    public synchronized boolean available(long itemId) {
        var definition = Objects.requireNonNull(definitions, "Item definitions not loaded").get(itemId);
        return definition != null && definition.resourceAvailable();
    }

    public synchronized boolean fits(long itemId, int slot, AccountGender gender) {
        if (!available(itemId)) {
            return false;
        }

        var definition = definitions.get(itemId);
        return definition.equipmentSlot() != null && definition.equipmentSlot() == slot
            && switch (definition.gender()) {
            case UNRESTRICTED -> true;
            case MALE -> gender == AccountGender.MALE;
            case FEMALE -> gender == AccountGender.FEMALE;
        };
    }

    public void validate(CharacterInventory inventory, AccountGender gender) {
        for (var item : inventory.items()) {
            if (!available(item.itemId()) || inventory.kind() == InventoryKind.EQUIPMENT
                && (item.quantity() != 1 || !fits(item.itemId(), item.slot(), gender))) {
                throw new IllegalArgumentException("Unsupported stored inventory item");
            }
        }
    }
}

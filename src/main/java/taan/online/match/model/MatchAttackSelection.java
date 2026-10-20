package taan.online.match.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MatchAttackSelection(List<Integer> activeItems, Map<Integer, MatchItemReceipt> consumedSlots) {
    public static final int MAX_ACTIVATIONS = 4096;
    public static final MatchAttackSelection NORMAL = new MatchAttackSelection(List.of(), Map.of());
    public static final MatchAttackSelection SPECIAL = new MatchAttackSelection(List.of(6), Map.of());

    public MatchAttackSelection {
        activeItems = List.copyOf(activeItems);
        consumedSlots = Map.copyOf(consumedSlots);

        if (activeItems.size() > MAX_ACTIVATIONS) {
            throw new ScenarioWorldException("ACTIVE_ITEM_LIMIT");
        }

        if (activeItems.stream().anyMatch(item -> !MatchItemSelectionRules.supports(item))) {
            throw new IllegalArgumentException("Unsupported active item");
        }
    }

    public MatchAttackSelection activatedSpecial() {
        var next = new ArrayList<>(activeItems);
        next.add(6);
        return new MatchAttackSelection(next, consumedSlots);
    }

    public MatchAttackSelection activated(int index, MatchItemReceipt receipt) {
        var next = new ArrayList<>(activeItems);
        next.add(receipt.itemId());
        var sources = new HashMap<>(consumedSlots);
        sources.put(index, receipt);
        return new MatchAttackSelection(next, sources);
    }

    public boolean duplicate(int index, int itemId) {
        var source = consumedSlots.get(index);
        return source != null && source.itemId() == itemId;
    }

    public List<Integer> selectedItems() {
        return MatchItemSelectionRules.selected(activeItems);
    }

    public int shotLimit() {
        return MatchItemSelectionRules.shotLimit(selectedItems());
    }

    public int attackKind() {
        return MatchItemSelectionRules.attackKind(selectedItems());
    }
}

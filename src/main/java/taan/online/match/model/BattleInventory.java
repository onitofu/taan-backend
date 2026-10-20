package taan.online.match.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BattleInventory(List<Integer> slots, List<MatchItemReceipt> receipts) {
    public static final int EMPTY = 65535;
    public static final int CAPACITY = 3;

    public BattleInventory {
        slots = List.copyOf(slots);
        receipts = List.copyOf(receipts);

        if (slots.size() != CAPACITY || receipts.size() > CAPACITY) {
            throw new IllegalArgumentException("Invalid battle inventory capacity");
        }
    }

    public static BattleInventory empty() {
        return new BattleInventory(List.of(EMPTY, EMPTY, EMPTY), List.of());
    }

    public int firstEmpty() {
        return slots.indexOf(EMPTY);
    }

    public BattleInventory inserted(MatchItemReceipt receipt) {
        if (receipt.inventoryIndex() != firstEmpty() || firstEmpty() < 0) {
            throw new IllegalArgumentException("Invalid battle inventory insertion");
        }

        var nextSlots = new ArrayList<>(slots);
        var nextReceipts = new ArrayList<>(receipts);
        nextSlots.set(receipt.inventoryIndex(), receipt.itemId());
        nextReceipts.add(receipt);
        return new BattleInventory(nextSlots, nextReceipts);
    }

    public BattleInventory confirmed(UUID admission, int index, int itemId) {
        var pending = receipts.stream().filter(receipt -> !receipt.confirmed()).findFirst();

        if (pending.isEmpty() || !pending.orElseThrow().admissionId().equals(admission)
            || pending.orElseThrow().inventoryIndex() != index
            || pending.orElseThrow().itemId() != itemId) {
            return this;
        }

        var next = new ArrayList<>(receipts);
        var receipt = pending.orElseThrow();
        next.set(next.indexOf(receipt), receipt.acknowledged());
        return new BattleInventory(slots, next);
    }

    public BattleInventory discarded(int index, int itemId) {
        var receipt = receipts.stream().filter(value -> value.inventoryIndex() == index).findFirst();

        if (receipt.isEmpty() || !receipt.orElseThrow().confirmed() || receipt.orElseThrow().itemId() != itemId) {
            return this;
        }

        var nextSlots = new ArrayList<>(slots);
        var nextReceipts = new ArrayList<>(receipts);
        nextSlots.set(index, EMPTY);
        nextReceipts.remove(receipt.orElseThrow());
        return new BattleInventory(nextSlots, nextReceipts);
    }
}

package taan.online.inventory.model;

import java.util.Comparator;
import java.util.List;

public record StarterEquipment(List<StarterItem> items) {
    public StarterEquipment {
        items = items.stream().sorted(Comparator.comparingInt(StarterItem::slot)).toList();

        if (items.stream().map(StarterItem::slot).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Duplicate starter equipment slot");
        }
    }
}

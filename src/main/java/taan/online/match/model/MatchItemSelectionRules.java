package taan.online.match.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MatchItemSelectionRules {
    private static final Map<Integer, Integer> PRIORITIES = Map.of(1, 301, 2, 300, 5, 150, 6, 100);

    private MatchItemSelectionRules() {
    }

    public static List<Integer> selected(List<Integer> active) {
        var sorted = active.stream().sorted(Comparator.comparingInt(PRIORITIES::get)).toList();
        var selected = new ArrayList<Integer>();
        int repeats = 0;
        boolean attack = false;

        for (int item : sorted) {
            if (item == 1 || item == 2) {
                if (repeats < 3) {
                    selected.add(item);
                    repeats++;
                }
            } else if (!attack) {
                selected.add(item);
                attack = true;
            }
        }

        return List.copyOf(selected);
    }

    public static Optional<MatchItemActivation> activate(
        MatchParticipant member,
        MatchWorldState world,
        MatchLootState loot,
        MatchAttackSelection selection,
        int index,
        int itemId
    ) {
        if (itemId == 6) {
            if (index != 255 || world.actors().get(member.sessionId()).energy() != member.maxEnergy()) {
                return Optional.empty();
            }

            return Optional.of(
                new MatchItemActivation(world.energy(member.sessionId(), 0), loot, selection.activatedSpecial())
            );
        }

        if (itemId != 1 && itemId != 2 && itemId != 5 || index < 0 || index >= BattleInventory.CAPACITY) {
            return Optional.empty();
        }

        var inventory = loot.inventory(member.sessionId());
        var receipt = inventory.receipts().stream().filter(value -> value.inventoryIndex() == index
            && value.itemId() == itemId && value.confirmed() && value.admissionId().equals(member.admissionId()))
            .findFirst();

        if (inventory.slots().get(index) != itemId || receipt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
            new MatchItemActivation(
                world,
                loot.discarded(member.sessionId(), index, itemId),
                selection.activated(index, receipt.orElseThrow())
            )
        );
    }
}

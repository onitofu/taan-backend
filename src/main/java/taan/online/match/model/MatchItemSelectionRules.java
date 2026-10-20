package taan.online.match.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MatchItemSelectionRules {
    private static final Map<Integer, Integer> PRIORITIES = Map.ofEntries(
        Map.entry(1, 301),
        Map.entry(2, 300),
        Map.entry(3, 400),
        Map.entry(5, 150),
        Map.entry(6, 100),
        Map.entry(7, 600),
        Map.entry(1005, 595),
        Map.entry(1010, 590),
        Map.entry(1015, 585),
        Map.entry(1020, 580),
        Map.entry(1025, 575),
        Map.entry(1030, 570),
        Map.entry(1035, 565),
        Map.entry(1040, 560),
        Map.entry(1045, 555),
        Map.entry(1050, 550)
    );

    private MatchItemSelectionRules() {
    }

    public static boolean isDamageUp(int item) {
        return item >= 1005 && item <= 1050 && item % 5 == 0;
    }

    public static boolean supports(int item) {
        return PRIORITIES.containsKey(item);
    }

    public static List<Integer> selected(List<Integer> active) {
        var sorted = active.stream().sorted(Comparator.comparingInt(PRIORITIES::get)).toList();
        var selected = new ArrayList<Integer>();
        int repeats = 0;
        int damageUps = 0;
        boolean attack = false;
        boolean spread = false;
        boolean guided = false;

        for (int item : sorted) {
            if (item == 1 || item == 2) {
                if (repeats < 3) {
                    selected.add(item);
                    repeats++;
                }
            } else if (item == 3) {
                if (!spread) {
                    selected.add(item);
                    spread = true;
                }
            } else if (item == 7) {
                if (!guided) {
                    selected.add(item);
                    guided = true;
                }
            } else if (isDamageUp(item)) {
                if (damageUps < 4) {
                    selected.add(item);
                    damageUps++;
                }
            } else if (!attack) {
                selected.add(item);
                attack = true;
            }
        }

        return List.copyOf(selected);
    }

    public static List<Integer> selected(List<Integer> active, boolean captain) {
        var selected = selected(active);

        if (!captain) {
            return selected;
        }

        var result = new ArrayList<Integer>();
        result.add(1);
        result.addAll(selected);
        return List.copyOf(result);
    }

    public static int shotLimit(List<Integer> selected) {
        int launches = 1 + selected.stream().filter(item -> item == 1 || item == 2).mapToInt(Integer::intValue).sum();
        return selected.contains(3) ? launches * 3 : launches;
    }

    public static int attackKind(List<Integer> selected) {
        return selected.contains(6) ? 2 : selected.contains(5) ? 1 : 0;
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

        if (!supports(itemId) || index < 0 || index >= BattleInventory.CAPACITY) {
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

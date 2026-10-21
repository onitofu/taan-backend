package taan.online.match.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

public final class MatchCardSelection {
    private final Set<Integer> slots;
    private final Map<Integer, Integer> cardsBySlot = new HashMap<>();
    private final Map<Integer, Integer> slotsByCard = new HashMap<>();

    public MatchCardSelection(Set<Integer> slots) {
        this.slots = Set.copyOf(slots);

        if (slots.stream().anyMatch(slot -> slot < 0 || slot > 7)) {
            throw new IllegalArgumentException("Invalid result participant slot");
        }
    }

    public MatchCardSelectionResult select(int slot, Integer requested, IntUnaryOperator random) {
        if (!slots.contains(slot) || requested != null && (requested < 0 || requested > 7)) {
            return new MatchCardSelectionResult(MatchCardSelectionOutcome.IGNORED, 0, slot, false);
        }

        var previous = cardsBySlot.get(slot);

        if (previous != null) {
            return new MatchCardSelectionResult(MatchCardSelectionOutcome.SELECTED, previous, slot, true);
        }

        List<Integer> free = IntStream.range(0, 8).filter(i -> !slotsByCard.containsKey(i)).boxed().toList();
        int card = requested == null ? free.get(random.applyAsInt(free.size())) : requested;

        if (slotsByCard.containsKey(card)) {
            return new MatchCardSelectionResult(MatchCardSelectionOutcome.ALREADY_OPEN, card, slot, false);
        }

        cardsBySlot.put(slot, card);
        slotsByCard.put(card, slot);
        return new MatchCardSelectionResult(MatchCardSelectionOutcome.SELECTED, card, slot, false);
    }
}

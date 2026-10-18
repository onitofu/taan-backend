package taan.online.room.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class CaptainSelection {
    private final UUID id;
    private final long selectionDeadlineNanos;
    private final long startDeadlineNanos;
    private final Set<Integer> candidateSlots = new HashSet<>();
    private CaptainSelectionPhase phase = CaptainSelectionPhase.SELECTING;
    private CaptainSelectionResult result;

    public CaptainSelection(UUID id, long now) {
        this.id = Objects.requireNonNull(id);
        selectionDeadlineNanos = now + 5_000_000_000L;
        startDeadlineNanos = now + 10_000_000_000L;
    }

    public UUID id() {
        return id;
    }

    public CaptainSelectionPhase phase() {
        return phase;
    }

    public long selectionDeadlineNanos() {
        return selectionDeadlineNanos;
    }

    public long startDeadlineNanos() {
        return startDeadlineNanos;
    }

    public boolean selectionEnded(long now) {
        return now - selectionDeadlineNanos >= 0;
    }

    public boolean expired(long now) {
        return now - startDeadlineNanos >= 0;
    }

    public boolean candidate(int slot) {
        return candidateSlots.contains(slot);
    }

    public boolean setCandidate(int slot, boolean candidate) {
        if (slot < 0 || slot > 7 || phase != CaptainSelectionPhase.SELECTING) {
            throw new IllegalStateException("Captain candidature is not available");
        }

        return candidate ? candidateSlots.add(slot) : candidateSlots.remove(slot);
    }

    public CaptainSelectionResult finalizeSelection(List<RoomSlot> slots, RandomGenerator random) {
        if (phase == CaptainSelectionPhase.SELECTING) {
            result = new CaptainSelectionResult(
                select(slots, RoomTeam.BLUE, random),
                select(slots, RoomTeam.RED, random)
            );
            phase = CaptainSelectionPhase.FINALIZED;
        }

        return result;
    }

    public CaptainSelectionResult result() {
        return result;
    }

    private int select(List<RoomSlot> slots, RoomTeam team, RandomGenerator random) {
        var members = slots.stream().filter(slot -> slot.participant() != null && slot.team() == team).toList();
        var volunteers = members.stream().filter(slot -> candidateSlots.contains(slot.index())).toList();
        var candidates = volunteers.isEmpty() ? members : volunteers;
        return candidates.get(random.nextInt(candidates.size())).index();
    }
}

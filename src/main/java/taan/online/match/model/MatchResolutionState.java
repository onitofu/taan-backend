package taan.online.match.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import taan.online.room.model.CaptainSelectionResult;

public record MatchResolutionState(Optional<MatchOutcome> pendingOutcome, Map<UUID, MatchExitKind> exits) {
    public MatchResolutionState {
        Objects.requireNonNull(pendingOutcome);
        exits = Map.copyOf(exits);
    }

    public static MatchResolutionState initial() {
        return new MatchResolutionState(Optional.empty(), Map.of());
    }

    public MatchResolutionState evaluate(List<MatchParticipant> roster, MatchWorldState world, int decisiveSlot) {
        return evaluate(roster, world, decisiveSlot, null);
    }

    public MatchResolutionState evaluate(
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        CaptainSelectionResult captains
    ) {
        return evaluate(roster, world, decisiveSlot, MatchOutcomeCause.ELIMINATION, captains);
    }

    public MatchResolutionState settled(List<MatchParticipant> roster, MatchWorldState world, int decisiveSlot) {
        return settled(roster, world, decisiveSlot, null);
    }

    public MatchResolutionState settled(
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        CaptainSelectionResult captains
    ) {
        if (pendingOutcome.filter(outcome -> outcome.cause() == MatchOutcomeCause.ELIMINATION).isEmpty()) {
            return evaluate(roster, world, decisiveSlot, captains);
        }

        var canonical = new MatchResolutionState(
            Optional.empty(),
            exits
        ).evaluate(roster, world, decisiveSlot, captains);
        return new MatchResolutionState(canonical.pendingOutcome(), exits);
    }

    private MatchResolutionState evaluate(
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        MatchOutcomeCause cause,
        CaptainSelectionResult captains
    ) {
        if (pendingOutcome.isPresent()) {
            return this;
        }

        var alive = roster.stream().filter(p -> captains == null || p.slotIndex() == captains.blueLeaderSlot()
            || p.slotIndex() == captains.redLeaderSlot()).filter(p -> !world.actors().get(p.sessionId()).dead()
            && !exits.containsKey(p.sessionId())).map(MatchParticipant::team).collect(Collectors.toSet());

        if (alive.size() > 1) {
            return this;
        }

        var shooter = roster.stream().filter(p -> p.slotIndex() == decisiveSlot).findFirst().orElseThrow();
        var winner = alive.isEmpty()
            ? roster.stream().map(MatchParticipant::team).filter(team -> team != shooter.team())
                .findFirst().orElseThrow()
            : alive.iterator().next();
        return new MatchResolutionState(Optional.of(new MatchOutcome(Set.of(winner), false, cause)), exits);
    }

    public MatchResolutionState exclude(
        Set<UUID> sessions,
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        CaptainSelectionResult captains
    ) {
        if (!roster.stream().map(MatchParticipant::sessionId).collect(Collectors.toSet()).containsAll(sessions)) {
            throw new IllegalArgumentException("Unknown match participant");
        }

        var updated = new HashMap<>(exits);
        sessions.forEach(session -> updated.put(session, MatchExitKind.DEPARTED));
        var next = new MatchResolutionState(pendingOutcome, updated);
        boolean nobody = roster.stream().allMatch(p -> updated.get(p.sessionId()) == MatchExitKind.DEPARTED);
        return nobody ? next : next.evaluate(roster, world, decisiveSlot, MatchOutcomeCause.DEPARTURE, captains);
    }

    public MatchResolutionState exit(
        UUID session,
        MatchExitKind kind,
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot
    ) {
        return exit(session, kind, roster, world, decisiveSlot, null);
    }

    public MatchResolutionState exit(
        UUID session,
        MatchExitKind kind,
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        CaptainSelectionResult captains
    ) {
        if (roster.stream().noneMatch(p -> p.sessionId().equals(session))) {
            throw new IllegalArgumentException("Unknown match participant");
        }

        Objects.requireNonNull(kind);
        var previous = exits.get(session);

        if (previous == MatchExitKind.DEPARTED || previous == kind) {
            return this;
        }

        var updated = new HashMap<>(exits);
        updated.put(session, kind);
        return new MatchResolutionState(pendingOutcome, updated).evaluate(
            roster,
            world,
            decisiveSlot,
            kind == MatchExitKind.SURRENDERED ? MatchOutcomeCause.SURRENDER : MatchOutcomeCause.DEPARTURE,
            captains
        );
    }
}

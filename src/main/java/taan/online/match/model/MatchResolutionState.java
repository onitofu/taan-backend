package taan.online.match.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record MatchResolutionState(Optional<MatchOutcome> pendingOutcome, Map<UUID, MatchExitKind> exits) {
    public MatchResolutionState {
        Objects.requireNonNull(pendingOutcome);
        exits = Map.copyOf(exits);
    }

    public static MatchResolutionState initial() {
        return new MatchResolutionState(Optional.empty(), Map.of());
    }

    public MatchResolutionState evaluate(List<MatchParticipant> roster, MatchWorldState world, int decisiveSlot) {
        return evaluate(roster, world, decisiveSlot, MatchOutcomeCause.ELIMINATION);
    }

    public MatchResolutionState settled(List<MatchParticipant> roster, MatchWorldState world, int decisiveSlot) {
        if (pendingOutcome.filter(outcome -> outcome.cause() == MatchOutcomeCause.ELIMINATION).isEmpty()) {
            return evaluate(roster, world, decisiveSlot);
        }

        var canonical = initial().evaluate(roster, world, decisiveSlot);
        return new MatchResolutionState(canonical.pendingOutcome(), exits);
    }

    private MatchResolutionState evaluate(
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot,
        MatchOutcomeCause cause
    ) {
        if (pendingOutcome.isPresent()) {
            return this;
        }

        var alive = roster.stream().filter(p -> !world.actors().get(p.sessionId()).dead()
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

    public MatchResolutionState exit(
        UUID session,
        MatchExitKind kind,
        List<MatchParticipant> roster,
        MatchWorldState world,
        int decisiveSlot
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
            kind == MatchExitKind.SURRENDERED ? MatchOutcomeCause.SURRENDER : MatchOutcomeCause.DEPARTURE
        );
    }
}

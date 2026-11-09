package taan.online.match.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import taan.online.room.model.RoomTeam;

public record GhostPickupWindow(
    long turnRevision,
    Map<Integer, List<GhostPathAlternative>> alternatives,
    List<List<Integer>> assignments,
    Map<RoomTeam, Set<Integer>> observedGauges
) {
    public GhostPickupWindow {
        var copy = new LinkedHashMap<Integer, List<GhostPathAlternative>>();
        alternatives.forEach((slot, paths) -> copy.put(slot, List.copyOf(paths)));
        alternatives = Collections.unmodifiableMap(copy);
        assignments = assignments.stream().map(List::copyOf).toList();
        var gauges = new LinkedHashMap<RoomTeam, Set<Integer>>();
        observedGauges.forEach((team, values) -> gauges.put(team, Set.copyOf(values)));
        observedGauges = Map.copyOf(gauges);
    }

    public static GhostPickupWindow create(
        long turn,
        List<MatchGhostState> ghosts,
        List<MatchItemBox> boxes,
        Map<RoomTeam, TeamGaugeState> gauges
    ) {
        var choices = new LinkedHashMap<Integer, List<GhostPathAlternative>>();
        List<List<Integer>> assignments = List.of(List.of());

        for (var ghost : ghosts.stream().sorted(Comparator.comparingInt(MatchGhostState::slotIndex)).toList()) {
            var paths = variants(ghost).stream()
                .map(variant -> new GhostPathAlternative(GhostPickupRules.plan(variant, boxes))).toList();
            choices.put(ghost.slotIndex(), paths);
            var extended = new ArrayList<List<Integer>>();

            for (var old : assignments) {
                for (int index = 0; index < paths.size(); index++) {
                    var next = new ArrayList<>(old);
                    next.add(index);
                    extended.add(List.copyOf(next));
                }
            }

            assignments = List.copyOf(extended);
        }

        var observed = new LinkedHashMap<RoomTeam, Set<Integer>>();
        gauges.forEach((team, value) -> observed.put(team, Set.copyOf(List.of(value.committed(), value.pending()))));
        return new GhostPickupWindow(turn, choices, assignments, observed);
    }

    public Optional<GhostPickupWindow> authorize(int slot, long box, long now) {
        var compatible = new ArrayList<List<Integer>>();

        for (var assignment : assignments) {
            var claims = new ArrayList<GhostPickupClaim>();
            int index = 0;

            for (var paths : alternatives.values()) {
                claims.addAll(paths.get(assignment.get(index++)).pickups());
            }

            boolean accepted = GhostPickupRules.arbitrate(claims).stream()
                .anyMatch(pickup -> pickup.boxEntityId() == box && pickup.slotIndex() == slot && pickup.tick() <= now);

            if (accepted) {
                compatible.add(assignment);
            }
        }

        return compatible.isEmpty() ? Optional.empty()
            : Optional.of(new GhostPickupWindow(turnRevision, alternatives, compatible, observedGauges));
    }

    public GhostPickupWindow without(int slot) {
        if (!alternatives.containsKey(slot)) {
            return this;
        }

        int index = new ArrayList<>(alternatives.keySet()).indexOf(slot);
        var remaining = new LinkedHashMap<>(alternatives);
        remaining.remove(slot);
        var choices = assignments.stream().map(old -> {
            var next = new ArrayList<>(old);
            next.remove(index);
            return List.copyOf(next);
        }).distinct().toList();
        return new GhostPickupWindow(turnRevision, remaining, choices, observedGauges);
    }

    public GhostPickupWindow observed(RoomTeam team, int value) {
        return observed(Map.of(team, new TeamGaugeState(value, value)));
    }

    public GhostPickupWindow observed(Map<RoomTeam, TeamGaugeState> states) {
        var gauges = new LinkedHashMap<>(observedGauges);
        states.forEach((team, state) -> {
            var values = new HashSet<>(gauges.getOrDefault(team, Set.of()));
            values.add(state.committed());
            values.add(state.pending());
            gauges.put(team, Set.copyOf(values));
        });
        return new GhostPickupWindow(turnRevision, alternatives, assignments, gauges);
    }

    private static List<MatchGhostState> variants(MatchGhostState ghost) {
        if (ghost.segment().isEmpty() || ghost.segment().orElseThrow().durationMillis() <= 750) {
            return List.of(ghost);
        }

        var segment = ghost.segment().orElseThrow();
        var result = new ArrayList<MatchGhostState>();

        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                var variant = new GhostSegment(
                    segment.start(),
                    segment.end(),
                    segment.startTick(),
                    segment.endTick(),
                    segment.facing(),
                    segment.easing(),
                    x,
                    y
                );
                var candidate = new MatchGhostState(
                    ghost.sessionId(),
                    ghost.slotIndex(),
                    ghost.position(),
                    ghost.target(),
                    ghost.facing(),
                    Optional.of(variant),
                    ghost.queuedForShot()
                );
                result.add(candidate);
            }
        }

        return List.copyOf(result);
    }
}

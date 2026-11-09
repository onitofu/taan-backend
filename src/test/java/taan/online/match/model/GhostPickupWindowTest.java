package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.room.model.RoomTeam;

class GhostPickupWindowTest {
    @Test
    void maintainsOneConsistentHiddenFrequencyAssignmentAcrossAllPickups() {
        var paths = new LinkedHashMap<Integer, List<GhostPathAlternative>>();
        paths.put(0, List.of(path(0, 1), path(0, 3)));
        paths.put(1, List.of(path(1, 2)));
        var window = new GhostPickupWindow(7, paths, List.of(List.of(0, 0), List.of(1, 0)), Map.of());
        var first = window.authorize(0, 100, 10).orElseThrow();
        assertEquals(List.of(List.of(0, 0)), first.assignments());
        assertTrue(first.authorize(1, 200, 10).isEmpty());
        assertTrue(window.authorize(1, 200, 10).isPresent());
        assertTrue(window.authorize(0, 999, 10).isEmpty());
        assertTrue(window.authorize(0, 100, 0).isEmpty());
        assertSame(window, window.without(7));
        assertEquals(1, window.without(0).alternatives().size());
    }

    @Test
    void usesEarliestIntersectionThenSmallestSlot() {
        var claims = List.of(
            new GhostPickupClaim(1, 5, 100),
            new GhostPickupClaim(1, 2, 100),
            new GhostPickupClaim(2, 0, 200),
            new GhostPickupClaim(2, 7, 100),
            new GhostPickupClaim(2, 6, 300)
        );
        assertEquals(List.of(new GhostPickupClaim(1, 2, 100), new GhostPickupClaim(2, 7, 100)),
            GhostPickupRules.arbitrate(claims));
    }

    @Test
    void samplesOnlyMovingGhostsInsideAggregateActiveBoxBounds() {
        var start = new GhostPoint(100, 100);
        var state = new MatchGhostState(UUID.randomUUID(), 1, start, start, 0, Optional.empty(), false);
        var box = new MatchItemBox(0x0500000000000001L, 100, 100, true);
        assertTrue(GhostPickupRules.plan(state, List.of(box)).isEmpty());
        var segment = GhostMovementRules.plan(new GhostMotionRequest(start, new GhostPoint(200, 100), 0, 200, 0, 0))
            .segment().orElseThrow();
        var moving = new MatchGhostState(state.sessionId(), 1, start, segment.end(), 0, Optional.of(segment), true);
        assertTrue(GhostPickupRules.plan(moving, List.of()).isEmpty());
        assertTrue(GhostPickupRules.plan(moving, List.of(box.pickedUp(1))).isEmpty());
        assertEquals(0, GhostPickupRules.plan(moving, List.of(box)).getFirst().tick());
        var stationary = new MatchGhostState(UUID.randomUUID(), 2, start, start, 0, Optional.empty(), false);
        var window = GhostPickupWindow.create(1, List.of(moving, stationary), List.of(box), Map.of(
            RoomTeam.BLUE,
            TeamGaugeState.empty(),
            RoomTeam.RED,
            TeamGaugeState.empty()
        ));
        assertFalse(window.assignments().isEmpty());
        assertEquals(0, window.observedGauges().get(RoomTeam.BLUE).iterator().next());
        var observed = window.observed(RoomTeam.BLUE, 5);
        assertTrue(observed.observedGauges().get(RoomTeam.BLUE).contains(5));
        assertFalse(window.observedGauges().get(RoomTeam.BLUE).contains(5));
        assertSame(state, state.advance(100));
        assertEquals(segment.end(), moving.completed().position());
        assertEquals(start, state.completed().position());
    }

    private static GhostPathAlternative path(int slot, long tick) {
        return new GhostPathAlternative(List.of(new GhostPickupClaim(100, slot, tick),
            new GhostPickupClaim(200, slot, tick)));
    }
}

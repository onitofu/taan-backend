package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GhostRulesBoundaryTest {
    @Test
    void movesLeftAndPreservesDirectionDuringVerticalMovement() {
        var start = new GhostPoint(300, 300);
        var left = GhostMovementRules.plan(new GhostMotionRequest(start, new GhostPoint(100, 300), 0, 200, 0, 0));
        assertEquals(1, left.segment().orElseThrow().facing());
        var up = GhostMovementRules.plan(new GhostMotionRequest(start, new GhostPoint(300, 100), 1, 200, 0, 0));
        assertEquals(1, up.segment().orElseThrow().facing());
    }

    @Test
    void filtersEverySideOfTheHalfOpenAggregateBoundsAndDoesNotTreatTheEndSentinelAsTheEndpoint() {
        var start = new GhostPoint(100, 100);
        var segment = GhostMovementRules.plan(new GhostMotionRequest(start, new GhostPoint(300, 100), 0, 200, 0, 0))
            .segment();
        var ghost = new MatchGhostState(UUID.randomUUID(), 0, start, new GhostPoint(300, 100), 0, segment, true);

        for (int[] center : new int[][]{{-100, 100}, {700, 100}, {100, -100}, {100, 700}}) {
            assertTrue(GhostPickupRules.plan(ghost, List.of(new MatchItemBox(
                0x0500000000000001L,
                center[0],
                center[1],
                true
            ))).isEmpty());
        }

        var claims = List.of(new GhostPickupClaim(1, 1, 1), new GhostPickupClaim(1, 2, 1));
        assertEquals(List.of(claims.getFirst()), GhostPickupRules.arbitrate(claims));
        assertTrue(GhostPickupRules.plan(ghost, List.of(new MatchItemBox(0x0500000000000001L, 300, 100, true)))
            .stream().allMatch(claim -> claim.tick() < segment.orElseThrow().endTick()));
    }

    @Test
    void sortsAllItemSourcesWithSharedCategoryLimitsAndClearsOnlyRequestedSources() {
        assertEquals(List.of(1, 5, 1, 1005, 7), MatchItemSelectionRules.selectedSources(
            List.of(7, 1005),
            List.of(5, 7),
            List.of(1, 5),
            true,
            List.of()
        ));
        assertEquals(List.of(1, 5, 1005, 7), MatchItemSelectionRules.selectedSources(
            List.of(1005),
            List.of(),
            List.of(7, 5),
            true,
            List.of()
        ));
        var owner = UUID.randomUUID();
        var receipt = new MatchItemReceipt(
            1,
            1,
            0,
            5,
            true,
            UUID.randomUUID(),
            MatchItemSource.GHOST_PICKUP,
            OptionalLong.empty(),
            Optional.empty()
        );
        var state = MatchTeamItemState.empty().activated(
            new MatchSharedItemReceipt(owner, MatchItemRoute.TEAM, receipt)
        ).activated(new MatchSharedItemReceipt(owner, MatchItemRoute.GHOST_TEAM, receipt));
        var retained = state.cleared(false, false);
        assertEquals(2, retained.receipts().size());
        assertEquals(List.of(5), state.cleared(false, true).team());
        assertEquals(List.of(5), state.cleared(true, false).ghostTeam());
        assertTrue(state.cleared(true, true).receipts().isEmpty());
        var full = new MatchTeamItemState(Collections.nCopies(MatchAttackSelection.MAX_ACTIVATIONS, 5), List.of());
        assertThrows(ScenarioWorldException.class, () -> full.activated(
            new MatchSharedItemReceipt(owner, MatchItemRoute.TEAM, receipt)
        ));
        assertFalse(state.team().isEmpty());
    }
}

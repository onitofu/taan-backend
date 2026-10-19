package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class MatchResolutionTest {
    @Test
    void keepsExitsImmutableAndNeverDowngradesADepartureOrRepeatsASurrender() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var snapshot = f.scenario.matches.current(token).orElseThrow();
            var initial = MatchResolutionState.initial();
            var first = initial.exit(f.owner, MatchExitKind.SURRENDERED, snapshot.participants(), snapshot.world(), 0);
            assertSame(
                first,
                first.exit(f.owner, MatchExitKind.SURRENDERED, snapshot.participants(), snapshot.world(), 0)
            );
            var gone = first.exit(f.owner, MatchExitKind.DEPARTED, snapshot.participants(), snapshot.world(), 0);
            assertEquals(first.pendingOutcome(), gone.pendingOutcome());
            assertSame(
                gone,
                gone.exit(f.owner, MatchExitKind.SURRENDERED, snapshot.participants(), snapshot.world(), 0)
            );
            assertSame(gone, gone.exit(f.owner, MatchExitKind.DEPARTED, snapshot.participants(), snapshot.world(), 0));
            assertTrue(initial.exits().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> gone.exits().clear());
            assertThrows(
                IllegalArgumentException.class,
                () -> initial.exit(
                    UUID.randomUUID(),
                    MatchExitKind.DEPARTED,
                    snapshot.participants(),
                    snapshot.world(),
                    0
                )
            );
            var other = snapshot.participants().get(1);
            var actor = snapshot.actors().get(f.owner);
            var world = snapshot.world().placed(
                f.owner,
                new MatchActorState(actor.x(), actor.y(), actor.facing(), 0, 0)
            );
            assertEquals(
                Set.of(other.team()),
                initial.evaluate(snapshot.participants(), world, other.slotIndex())
                .pendingOutcome().orElseThrow().winningTeams()
            );
        }
    }

    @Test
    void rejectsAnOutcomeWhoseCauseAndWinnerDisagree() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchOutcome(Set.of(), false, MatchOutcomeCause.ELIMINATION)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchOutcome(Set.of(RoomTeam.BLUE), false, MatchOutcomeCause.TECHNICAL_CANCELLATION)
        );
    }

    @Test
    void keepsTheShotWinnerAcrossAnExitButIncludesCanonicalLandingDeathInTheFinalOutcome() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var snapshot = f.scenario.matches.current(token).orElseThrow();
            var shooter = snapshot.participants().getFirst();
            var target = snapshot.participants().get(1);
            var a = snapshot.actors().get(shooter.sessionId());
            var b = snapshot.actors().get(target.sessionId());
            var world = snapshot.world().placed(
                target.sessionId(),
                new MatchActorState(b.x(), b.y(), b.facing(), 0, 0)
            );
            var decided = MatchResolutionState.initial().evaluate(snapshot.participants(), world, shooter.slotIndex());
            var departed = decided.exit(
                shooter.sessionId(),
                MatchExitKind.DEPARTED,
                snapshot.participants(),
                world,
                shooter.slotIndex()
            );
            assertEquals(Set.of(shooter.team()), departed.pendingOutcome().orElseThrow().winningTeams());
            var landed = world.placed(shooter.sessionId(), new MatchActorState(a.x(), a.y(), a.facing(), 0, 0));
            var settled = departed.settled(snapshot.participants(), landed, shooter.slotIndex());
            assertEquals(Set.of(target.team()), settled.pendingOutcome().orElseThrow().winningTeams());
            assertEquals(departed.exits(), settled.exits());
        }
    }
}

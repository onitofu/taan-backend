package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomState;

class MatchOutcomeTest {
    @Test
    void commitsTheWinnerOnceThenWaitsForBothAcksAndStartsAnotherMatchWithOneMedal() throws Exception {
        try (var f = new MatchOutcomeFixture(); var logs = new LogCapture()) {
            var before = f.snapshot();
            var shooter = f.active();
            var team = before.participants().stream().filter(p -> p.sessionId().equals(shooter))
                .findFirst().orElseThrow().team();
            var timer = f.room.scenario.matchTimers.commands.getFirst();
            var result = f.shot(10000, false, false);
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
            assertEquals(Set.of(team), f.snapshot().resolution().pendingOutcome().orElseThrow().winningTeams());
            assertEquals(0, f.owner.ends);
            assertEquals(0, f.guest.ends);
            var oldAck = f.ackCommand(shooter);
            f.ack(shooter);
            assertEquals(0, f.owner.ends);
            f.ack(f.room.players.stream().filter(id -> !id.equals(shooter)).findFirst().orElseThrow());
            assertTrue(f.matches.current(f.token).isEmpty());
            assertEquals(1, f.owner.ends);
            assertEquals(1, f.guest.ends);
            assertEquals(Set.of(team), f.owner.outcomes.getFirst().winningTeams());
            assertEquals(f.owner.outcomes, f.guest.outcomes);
            assertTrue(f.owner.outcomes.getFirst().awardsGranted());
            assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(f.room.owner).orElseThrow().state());
            assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
            assertTrue(logs.text().contains("match.outcome.decided"));
            assertTrue(logs.text().contains("match.finished"));
            assertFalse(logs.text().contains("match.cancelled"));
            var oldToken = f.token;
            f.room.scenario.rooms.setReady(f.room.players.get(1), true);
            f.start();
            assertFalse(oldToken.equals(f.token));
            timer.run();
            assertFalse(f.matches.confirmScenario(oldAck).toCompletableFuture().join());
            assertTrue(f.matches.current(f.token).isPresent());
            assertTrue(f.snapshot().resolution().exits().isEmpty());
            assertTrue(f.snapshot().resolution().pendingOutcome().isEmpty());
            assertEquals(1, f.owner.ends);
        }
    }

    @Test
    void makesTheShooterLoseOnSuicideAndOnSimultaneousDeathsAndWaitsForFalling() throws Exception {
        for (boolean both : new boolean[]{false, true}) {
            try (var f = new MatchOutcomeFixture()) {
                var shooter = f.active();
                var other = f.snapshot().participants().stream().filter(p -> !p.sessionId().equals(shooter))
                    .findFirst().orElseThrow();
                f.shot(both ? 10000 : 0, true, true);
                f.ack(f.room.owner);
                f.ack(f.room.players.get(1));
                assertEquals(0, f.owner.ends);
                f.room.scenario.matchTimers.advanceSeconds(2);
                assertEquals(Set.of(other.team()), f.owner.outcomes.getFirst().winningTeams());
                assertEquals(MatchOutcomeCause.ELIMINATION, f.owner.outcomes.getFirst().cause());
                assertEquals(1, f.owner.ends);
            }
        }
    }

    @Test
    void surrenderEndsTheGameButKeepsBothPlayersInTheRoom() {
        try (var f = new MatchOutcomeFixture()) {
            var loser = f.room.owner;
            var winner = f.snapshot().participants().stream().filter(p -> !p.sessionId().equals(loser))
                .findFirst().orElseThrow();
            var context = f.context(loser);
            assertTrue(f.surrender(loser).toCompletableFuture().join().termination() != null);
            assertEquals(Set.of(winner.team()), f.owner.outcomes.getFirst().winningTeams());
            assertEquals(MatchOutcomeCause.SURRENDER, f.owner.outcomes.getFirst().cause());
            assertTrue(f.room.players.stream().allMatch(id -> f.room.scenario.rooms.membership(id).isPresent()));
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(f.guest.closed.isEmpty());
            new TerminateMatchAction(f.matches).execute(loser, context).toCompletableFuture().join();
            assertEquals(1, f.owner.ends);
        }
    }
}

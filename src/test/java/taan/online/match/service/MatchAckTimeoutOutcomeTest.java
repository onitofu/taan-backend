package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.match.model.MatchResolutionState;
import taan.online.room.model.RoomState;

class MatchAckTimeoutOutcomeTest {
    @Test
    void preservesALethalWinnerOrAwardsTheOnlyAcknowledgingParticipantRegardlessOfWhoShot() throws Exception {
        for (boolean lethal : new boolean[]{false, true}) {
            for (boolean shooterMissing : new boolean[]{false, true}) {
                try (var f = new MatchOutcomeFixture()) {
                    var shooter = f.active();
                    var other = f.snapshot().participants().stream().filter(p -> !p.sessionId().equals(shooter))
                        .findFirst().orElseThrow().sessionId();
                    var missing = shooterMissing ? shooter : other;
                    var present = shooterMissing ? other : shooter;
                    var winner = lethal ? shooter : present;
                    var team = f.snapshot().participants().stream().filter(p -> p.sessionId().equals(winner))
                        .findFirst().orElseThrow().team();
                    f.shot(lethal ? 10000 : 0, false, true);
                    var timer = f.room.scenario.matchTimers.commands.getFirst();
                    f.ack(present);
                    f.room.scenario.matchTimers.advanceSeconds(30);

                    var output = missing.equals(f.room.owner) ? f.owner : f.guest;
                    var survivor = present.equals(f.room.owner) ? f.owner : f.guest;
                    assertEquals(List.of("SCENARIO_COMPLETION_TIMEOUT"), output.closed);
                    assertEquals(0, output.ends);
                    assertEquals(1, survivor.ends);
                    assertEquals(Set.of(team), survivor.outcomes.getFirst().winningTeams());
                    assertFalse(survivor.outcomes.getFirst().awardsGranted());
                    assertTrue(f.matches.current(f.token).isEmpty());
                    assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
                    assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(present).orElseThrow().state());

                    timer.run();
                    f.disconnect(missing);
                    f.disconnect(missing);
                    assertEquals(1, survivor.ends);
                    assertEquals(present, f.room.scenario.rooms.current(present).orElseThrow().ownerSessionId());
                }
            }
        }
    }

    @Test
    void releasesBothMissingPlayersTogetherWithoutInventingAWinnerAndNeverTouchesARematch() throws Exception {
        for (boolean lethal : new boolean[]{false, true}) {
            try (var f = new MatchOutcomeFixture(); var logs = new LogCapture()) {
                f.shot(lethal ? 10000 : 0, false, true);
                var timer = f.room.scenario.matchTimers.commands.getFirst();
                f.room.scenario.matchTimers.advanceSeconds(30);

                assertTrue(f.matches.current(f.token).isEmpty());
                assertEquals(0, f.owner.ends);
                assertEquals(0, f.guest.ends);
                assertEquals(List.of("SCENARIO_COMPLETION_TIMEOUT"), f.owner.closed);
                assertEquals(List.of("SCENARIO_COMPLETION_TIMEOUT"), f.guest.closed);
                assertTrue(logs.text().contains(lethal ? "event=\"match.finished\"" : "event=\"match.cancelled\""));

                f.disconnect(f.room.owner);
                f.disconnect(f.room.players.get(1));
                var scenario = f.room.scenario;
                assertTrue(scenario.rooms.forChannel(1).isEmpty());
                var owner = scenario.player(3, 1);
                var guest = scenario.player(4, 1);
                scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                f.matches.subscribe(owner, new TestMatchOutput());
                f.matches.subscribe(guest, new TestMatchOutput());
                scenario.rooms.setReady(guest, true);
                scenario.start.execute(owner).delivery().toCompletableFuture().join();
                var next = scenario.rooms.matchContext(owner).token();
                MatchFixtures.loaded(f.matches, next);

                timer.run();
                assertTrue(f.matches.current(next).isPresent());
                assertTrue(f.matches.current(next).orElseThrow().resolution().exits().isEmpty());
                assertEquals(0, f.owner.ends);
            }
        }
    }

    @Test
    void refusesUnknownBatchMembersAndDoesNotMutateAnAlreadyStoppedOwner() throws Exception {
        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var snapshot = f.runtime.snapshot();
            assertThrows(
                IllegalArgumentException.class,
                () ->
                MatchResolutionState.initial().exclude(
                    Set.of(UUID.randomUUID()),
                    snapshot.participants(),
                    snapshot.world(),
                    snapshot.turn().activeSlot()
                )
            );

            f.runtime.stop();
            f.runtime.outcomes.timedOut(Set.of(f.context().sessionId()));
            assertTrue(f.runtime.snapshot().resolution().exits().isEmpty());
        }
    }
}

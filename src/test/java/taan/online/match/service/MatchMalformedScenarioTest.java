package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.match.controller.ApplyScenarioPacketHandler;
import taan.online.match.model.MatchExitKind;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchMalformedScenarioTest {
    @Test
    void cancelsOnlyForAMalformedScenarioFromTheCurrentAdmittedShooter() {
        try (var f = new MatchOutcomeFixture()) {
            var handler = new ApplyScenarioPacketHandler(new ApplyScenarioAction(f.matches));
            var snapshot = f.snapshot();
            var shooter = MatchCommandContext.from(snapshot, f.active());
            var other = snapshot.participants().stream().filter(p -> !p.sessionId().equals(shooter.sessionId()))
                .findFirst().orElseThrow();
            var peer = MatchCommandContext.from(snapshot, other.sessionId());
            long turn = snapshot.turn().revision();
            long world = snapshot.world().revision();
            long phase = snapshot.phaseRevision();

            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(peer, turn, world, phase, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(shooter, turn - 1, world, phase, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(shooter, turn, world - 1, phase, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(shooter, turn, world, phase - 1, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(0, f.owner.ends);

            assertEquals(
                ScenarioApplicationOutcome.REJECTED,
                handler.handle(shooter, turn, world, phase, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(1, f.owner.ends);
            assertEquals(1, f.guest.ends);
            assertTrue(f.owner.outcomes.getFirst().winningTeams().isEmpty());
            assertTrue(f.matches.current(f.token).isEmpty());
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(shooter, turn, world, phase, new byte[0]).toCompletableFuture().join().outcome()
            );
            assertEquals(1, f.owner.ends);
        }
    }

    @Test
    void ignoresWithdrawnUnadmittedAndPreviewSendersButCancelsANewMalformedAttemptAfterCommit() throws Exception {
        try (var f = new MatchRuntimeFixture(8)) {
            assertFalse(f.runtime.scenarios.malformed(f.context(), 0, 0, 0));
        }

        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CANNON_1V1)) {
            assertFalse(f.runtime.scenarios.malformed(f.context(), 0, 0, 0));
        }

        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var context = f.context();
            var snapshot = f.runtime.snapshot();
            long turn = snapshot.turn().revision();
            long world = snapshot.world().revision();
            long phase = snapshot.phaseRevision();
            f.admitted = false;
            assertFalse(f.runtime.scenarios.malformed(context, turn, world, phase));

            f.admitted = true;
            f.match.withdraw(context.sessionId(), MatchExitKind.SURRENDERED);
            f.runtime.refresh();
            assertFalse(f.runtime.scenarios.malformed(context, turn, world, phase));
        }

        try (var f = new MatchOutcomeFixture()) {
            f.shot(0, false, false);
            var snapshot = f.snapshot();
            var membership = f.context(f.room.owner).membership();
            var handler = new ApplyScenarioPacketHandler(new ApplyScenarioAction(f.matches));
            assertEquals(
                ScenarioApplicationOutcome.REJECTED,
                handler.handle(
                    MatchCommandContext.from(snapshot, f.active()),
                    snapshot.turn().revision(),
                    snapshot.world().revision(),
                    snapshot.phaseRevision(),
                    new byte[0]
                ).toCompletableFuture().join().outcome()
            );
            assertEquals(1, f.owner.ends);

            assertFalse(f.matches.requestDeparture(
                f.token,
                membership,
                RoomMatchEndReason.DISCONNECTED
            ).toCompletableFuture().join());
        }
    }
}

package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationMode;

class ScenarioBoundaryTest {
    @Test
    void refusesAnObsoleteWorldAfterCommitAndRevalidatesMembershipBeforeHandlingDeliveryFailure() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var command = fixture.command();
            var applied = f.runtime.applyScenario(command);
            f.run();
            var committed = applied.join().committed().orElseThrow();
            var stale = new ApplyScenarioCommand(
                command.context(),
                command.turnRevision(),
                command.worldRevision() + 10,
                command.claims(),
                command.root()
            );
            var ignored = f.runtime.applyScenario(stale);
            f.run();

            assertEquals(ScenarioApplicationOutcome.IGNORED, ignored.join().outcome());
            assertSame(committed.snapshot().world(), f.runtime.snapshot().world());
            f.admitted = false;
            f.runtime.scenarioDeliveryFailed(committed, new IllegalStateException("obsolete recipient"));
            f.run();
            assertTrue(f.runtime.running());
        }
    }

    @Test
    void refusesACommitWithAnObsoleteTurnEvenWhenItsWorldAndMembershipAreCurrent() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            var command = fixture.command();
            var accepted = new ValidateScenarioAction((context, member) -> true).execute(
                new ScenarioAdmissionContext(before, command.context(), command.turnRevision(), Optional.empty()),
                command.claims(),
                command.root()
            ).accepted().orElseThrow();
            var prepared = new PrepareScenarioAction().execute(before, accepted);
            var stale = new ApplyScenarioCommand(
                command.context(),
                command.turnRevision() + 1,
                command.worldRevision(),
                command.claims(),
                command.root()
            );

            assertFalse(f.runtime.commitScenario(stale, prepared, before.completed(prepared.world())));
            assertSame(before, f.runtime.snapshot());
        }
    }

    @Test
    void preservesCommittedWorldAndTerminatesWhenDeliveryFailureCannotEnterAFullMailbox() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            var command = fixture.command();
            var applied = f.runtime.applyScenario(command);
            f.run();
            var committed = applied.join().committed().orElseThrow();

            for (int index = 0; index < 8; index++) {
                f.runtime.applyScenario(command);
            }

            f.runtime.scenarioDeliveryFailed(committed, new IllegalStateException("transport failed"));
            f.run();
            assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, f.callbacks.reason);
            assertSame(committed.snapshot().world(), f.runtime.snapshot().world());
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
            assertFalse(logs.text().contains("transport failed"));
        }
    }

    @Test
    void expiresActionAndSynchronizationUsingTheirExistingTechnicalCleanupPolicies() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.PREVIEW_TIMEOUT, f.callbacks.reason);
        }

        try (var f = new MatchRuntimeFixture(8)) {
            f.activate();
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            f.guest.turnDelivery = new CompletableFuture<>();

            for (var member : f.runtime.snapshot().participants()) {
                var snapshot = f.runtime.snapshot();
                var context = new MatchCommandContext(
                    snapshot.id(),
                    snapshot.roomId(),
                    snapshot.roomGeneration(),
                    snapshot.channelId(),
                    member.sessionId(),
                    member.admissionId()
                );
                f.runtime.submit(new ConfirmMatchLoadingCommand(
                    context,
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                ));
            }

            f.run();
            assertEquals(MatchPhase.SYNCHRONIZING, f.runtime.snapshot().phase());
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.LOADING_TIMEOUT, f.callbacks.reason);
        }
    }

    @Test
    void returnsEstablishedGameplayPhasesToTheRoomButDisconnectsOnServerShutdown() {
        for (var phase : MatchPhase.values()) {
            var expected = switch (phase) {
                case RESERVED -> RoomMatchTerminationMode.ROLLBACK;
                case PREVIEW, ACTION, SETTLING, COMPLETION -> RoomMatchTerminationMode.RETURN_TO_ROOM;
                default -> RoomMatchTerminationMode.DISCONNECT;
            };
            assertEquals(expected, MatchTerminationDelivery.mode(phase, RoomMatchEndReason.INVALID_SCENARIO, false));
            assertEquals(
                RoomMatchTerminationMode.DISCONNECT,
                MatchTerminationDelivery.mode(phase, RoomMatchEndReason.SERVER_STOPPING, false)
            );
        }
    }
}

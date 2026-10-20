package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.LeaveUserCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.view.ProtocolMatchView;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;

class TeamLoadingBoundaryTest {
    @Test
    void rejectsPrematureStaleWrongPhaseAndStoppedLoadingTimers() {
        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CLIENT_SCENARIO)) {
            var before = f.runtime.progress();
            assertFalse(f.runtime.turns.loadingExpired(before));
            f.activate();
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            assertFalse(f.runtime.turns.loadingExpired(f.runtime.progress()));
            assertFalse(f.runtime.turns.loadingExpired(new RoomMatchProgressToken(f.token, -1)));
            f.runtime.stop();
            assertFalse(f.runtime.turns.loadingExpired(f.runtime.progress()));
        }
    }

    @Test
    void boundsFirstTurnDeliveryAfterLoadingAndCancelsLateReportsWithoutStartingPlay() {
        for (boolean timer : new boolean[]{false, true}) {
            try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CLIENT_SCENARIO)) {
                f.owner.turnDelivery = new CompletableFuture<>();
                f.guest.turnDelivery = new CompletableFuture<>();
                f.activate();
                f.runtime.submit(new StartMatchCommand(f.token));
                f.run();

                for (var member : f.runtime.snapshot().participants()) {
                    f.runtime.submit(new ConfirmMatchLoadingCommand(
                        MatchCommandContext.from(f.runtime.snapshot(), member.sessionId()),
                        member.slotIndex(),
                        member.spawn().settledX(),
                        member.spawn().settledY()
                    ));
                }

                f.run();
                assertEquals(MatchPhase.SYNCHRONIZING, f.runtime.snapshot().phase());
                f.timers.now = f.runtime.snapshot().deadlineNanos();

                if (timer) {
                    f.runtime.deadlineExpired(f.runtime.progress());
                } else {
                    f.runtime.submit(new ReportMatchEnemiesCommand(f.context()));
                }

                f.run();
                assertEquals(RoomMatchEndReason.LOADING_TIMEOUT, f.callbacks.reason);
                f.owner.turnDelivery.complete(null);
                f.guest.turnDelivery.complete(null);
                f.run();
                assertFalse(f.runtime.running());
            }
        }
    }

    @Test
    void cleansUpAnExcludedParticipantEvenWhenAnEarlierTerminationCallbackWasRejected() throws Exception {
        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            f.callbacks.rejectTermination = true;
            f.runtime.outcomes.timedOut(Set.copyOf(f.match.participants().stream()
                .map(member -> member.sessionId()).toList()));
            assertTrue(f.runtime.running());
            f.callbacks.rejectTermination = false;
            assertTrue(f.runtime.outcomes.depart(new DepartMatchCommand(f.context(), RoomMatchEndReason.DISCONNECTED)));
            assertEquals(RoomMatchEndReason.DISCONNECTED, f.callbacks.reason);
            assertFalse(f.runtime.running());
        }
    }

    @Test
    void acceptsAlreadyExcludedDisconnectsDuringSynchronizationAndWaitsForTheRemainingDeliveryCallbacks() {
        try (var f = new TeamMatchFixture(2, 2, RoomMode.ITEM)) {
            f.outputs.values().forEach(o -> o.turnDelivery = new CompletableFuture<>());
            f.start(false);
            var snapshot = f.snapshot();
            var missing = snapshot.participants().getFirst();

            for (var member : snapshot.participants()) {
                if (!member.sessionId().equals(missing.sessionId())) {
                    f.scenario.matches.loadingDone(
                        f.scenario.rooms.matchContext(member.sessionId()),
                        member.slotIndex(),
                        member.spawn().settledX(),
                        member.spawn().settledY()
                    ).toCompletableFuture().join();
                }
            }

            f.scenario.matchTimers.advanceSeconds(31);
            assertEquals(MatchPhase.SYNCHRONIZING, f.snapshot().phase());
            assertTrue(f.scenario.matches.requestDeparture(
                f.token,
                f.scenario.rooms.membership(missing.sessionId()).orElseThrow(),
                RoomMatchEndReason.DISCONNECTED
            ).toCompletableFuture().join());
            f.scenario.rooms.release(missing.sessionId(), "PEER_CLOSED");
            var output = new CollectingPacketOutput();
            new ProtocolMatchView(output).firstTurn(f.snapshot(), f.players.getLast());
            assertEquals(missing.slotIndex(), new LeaveUserCodec().decode(output.batches.getFirst().getFirst())
                .slotIndex());
            new TurnStartCodec().decode(output.batches.getFirst().getLast());

            f.outputs.values().forEach(o -> o.turnDelivery.complete(null));
            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
        }

        try (var f = new TeamMatchFixture(2, 2, RoomMode.ITEM)) {
            f.outputs.values().forEach(o -> o.turnDelivery = new CompletableFuture<>());
            f.start(false);

            for (var member : f.snapshot().participants()) {
                var next = f.scenario.matches.loadingDone(
                    f.scenario.rooms.matchContext(member.sessionId()),
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                ).toCompletableFuture().join();

                if (next != null) {
                    f.scenario.matches.publish(f.token);
                }
            }

            assertEquals(MatchPhase.SYNCHRONIZING, f.snapshot().phase());
            assertFalse(f.scenario.matches.requestDeparture(
                f.token,
                f.scenario.rooms.membership(f.owner).orElseThrow(),
                RoomMatchEndReason.DISCONNECTED
            ).toCompletableFuture().join());
            f.outputs.values().forEach(o -> o.turnDelivery.complete(null));
        }
    }
}

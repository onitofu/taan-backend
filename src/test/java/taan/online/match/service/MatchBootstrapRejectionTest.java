package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchBootstrapFixture;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomStartOutcome;

class MatchBootstrapRejectionTest {
    @Test
    void disconnectsAnExplicitlyEndedLoadingMatchEvenBeforeItsFirstStartWrite() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.room.scenario.matchTimers.onSchedule = () -> f.room.scenario.rooms.terminateMatch(
                f.token(),
                RoomMatchEndReason.GIVE_UP
            );
            f.start();

            assertTrue(f.outputs.stream().allMatch(output -> output.starts.isEmpty() && output.closed.size() == 1));
            assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
        }
    }

    @Test
    void rejectsMissingAndForgedBindingsBeforeChangingAnyBootstrapState() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            var rooms = f.room.scenario.rooms;
            var matches = f.room.scenario.matches;
            var token = rooms.reserveMatch(f.room.owner).token();
            var context = f.context(0);
            var reserved = matches.progress(token);
            assertTrue(matches.publish(reserved.match()).toCompletableFuture().isDone());
            assertFalse(matches.reportEnemies(context).toCompletableFuture().join());
            assertFalse(matches.confirmPosition(context, 0, 97, 685, 0).toCompletableFuture().join());
            assertNull(matches.loadingDone(context, 0, 97, 685).toCompletableFuture().join());
            assertNull(matches.deliveryCompleted(reserved, f.room.owner).toCompletableFuture().join());
            matches.unsubscribe(f.room.players.get(1));
            assertFalse(matches.activate(token, rooms.matchCallbacks()));
            matches.subscribe(f.room.players.get(1), f.outputs.get(1));
            assertTrue(matches.activate(token, rooms.matchCallbacks()));
            rooms.commitMatch(token).toCompletableFuture().join();
            assertFalse(matches.activate(token, rooms.matchCallbacks()));
            matches.publish(token);
            matches.publish(token);
            assertEquals(1, f.outputs.getFirst().starts.size());
            assertNull(matches.deliveryCompleted(matches.progress(token), f.room.owner).toCompletableFuture().join());
            assertNull(
                matches.deliveryCompleted(matches.progress(token), UUID.randomUUID()).toCompletableFuture().join()
            );

            var stale = new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID());
            var member = context.membership();

            for (var forged : new RoomMatchContext[]{
                new RoomMatchContext(member, stale),
                new RoomMatchContext(
                    new RoomMembership(
                        member.sessionId(),
                        member.roomId(),
                        member.generation(),
                        2,
                        member.admissionId()
                    ),
                    token
                ),
                new RoomMatchContext(
                    new RoomMembership(
                        member.sessionId(),
                        member.roomId(),
                        member.generation(),
                        member.channelId(),
                        UUID.randomUUID()
                    ),
                    token
                ),
                new RoomMatchContext(
                    new RoomMembership(
                        UUID.randomUUID(),
                        member.roomId(),
                        member.generation(),
                        member.channelId(),
                        member.admissionId()
                    ),
                    token
                )
            }) {
                assertNull(matches.loadingDone(forged, 0, 97, 685).toCompletableFuture().join());
                assertFalse(matches.reportEnemies(forged).toCompletableFuture().join());
                assertFalse(matches.confirmPosition(forged, 0, 97, 685, 0).toCompletableFuture().join());
                assertTrue(f.room.scenario.matches.loadingDone(forged, 0, 97, 685).toCompletableFuture().isDone());
                assertFalse(f.room.scenario.matches.reportEnemies(forged).toCompletableFuture().join());
                assertFalse(
                    f.room.scenario.matches.confirmPosition(forged, 0, 97, 685, 0).toCompletableFuture().join()
                );
            }

            var old = new RoomMatchProgressToken(token, -1);
            assertTrue(matches.publish(old.match()).toCompletableFuture().isDone());
            assertTrue(matches.publish(stale).toCompletableFuture().isDone());
            assertNull(matches.progress(stale));
            assertFalse(matches.activate(stale, rooms.matchCallbacks()));
            assertFalse(f.room.scenario.matches.reportEnemies(null).toCompletableFuture().join());
            assertFalse(f.room.scenario.matches.confirmPosition(null, 0, 97, 685, 0).toCompletableFuture().join());
            assertTrue(f.room.scenario.matches.loadingDone(null, 0, 97, 685).toCompletableFuture().isDone());
        }
    }

    @Test
    void boundsOpaqueReportsAndRejectsUnauthenticatedActionContexts() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            f.start();
            var rooms = f.room.scenario.rooms;
            var reports = new ReportMatchEnemiesAction(f.room.scenario.matches);
            var loading = new ConfirmMatchLoadingAction(f.room.scenario.matches);
            var position = new ConfirmMatchPositionAction(f.room.scenario.matches);
            var context = f.context(0);

            for (int index = 0; index < 8; index++) {
                assertTrue(reports.execute(f.room.owner, context).toCompletableFuture().join());
            }

            assertFalse(reports.execute(f.room.owner, context).toCompletableFuture().join());
            f.room.scenario.matchTimers.now = 1_000_000_000L;
            assertTrue(reports.execute(f.room.owner, context).toCompletableFuture().join());
            assertTrue(f.snapshot().loadedSessions().isEmpty());

            for (var invalid : new RoomMatchContext[]{null, context}) {
                UUID actor = invalid == null ? f.room.owner : UUID.randomUUID();
                assertFalse(reports.execute(actor, invalid).toCompletableFuture().join());
                assertFalse(position.execute(actor, invalid, 0, 97, 685, 0).toCompletableFuture().join());
                assertTrue(loading.execute(actor, invalid, 0, 97, 685).toCompletableFuture().isDone());
            }

            assertTrue(logs.text().contains("MATCH_REPORT_RATE_EXCEEDED"));
            f.ack(0);
            f.ack(1);
            rooms.terminateMatch(context.token(), RoomMatchEndReason.GIVE_UP);

            assertFalse(reports.execute(f.room.owner, context).toCompletableFuture().join());
            assertFalse(position.execute(f.room.owner, context, 0, 97, 685, 0).toCompletableFuture().join());
            assertTrue(loading.execute(f.room.owner, context, 0, 97, 685).toCompletableFuture().isDone());
        }
    }

    @Test
    void expiresBeforeApplyingLateRequestsEvenWhenTheTimerCommandHasNotRunYet() {
        for (int request = 0; request < 4; request++) {
            try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
                f.start();
                var context = f.context(0);
                var token = f.token();
                var rooms = f.room.scenario.rooms;

                if (request == 2) {
                    f.ack(0);
                    f.ack(1);
                    f.room.scenario.matchTimers.now = 20_000_000_000L;
                    assertFalse(
                        f.room.scenario.matches.confirmPosition(context, 0, 97, 685, 0).toCompletableFuture().join()
                    );
                } else {
                    f.room.scenario.matchTimers.now = 30_000_000_000L;

                    if (request == 0) {
                        assertFalse(f.room.scenario.matches.reportEnemies(context).toCompletableFuture().join());
                    } else if (request == 1) {
                        assertTrue(
                            f.room.scenario.matches.loadingDone(context, 0, 97, 685).toCompletableFuture().isDone()
                        );
                    } else {
                        assertNull(
                            f.room.scenario.matches.deliveryCompleted(
                                f.room.scenario.matches.progress(token),
                                f.room.owner
                            ).toCompletableFuture().join()
                        );
                    }
                }

                assertTrue(f.room.scenario.matches.current(token).isEmpty());
                assertEquals(request == 2 ? 1 : 0, f.outputs.getFirst().ends);
            }
        }
    }

    @Test
    void rollsBackWithoutDisconnectBeforeStartButClosesPartialBootstrapAfterSchedulingFailure() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.room.scenario.matchTimers.rejectSchedule = true;
            f.start().delivery().toCompletableFuture().join();
            assertTrue(f.outputs.stream().allMatch(output -> output.starts.isEmpty() && output.closed.isEmpty()));
            assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(f.room.owner).orElseThrow().state());
        }

        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.start();
            var token = f.token();
            f.ack(0);
            f.room.scenario.matchTimers.rejectSchedule = true;
            f.ack(1).toCompletableFuture().join();

            assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1 && output.ends == 0));
            assertTrue(f.room.scenario.matches.current(token).isEmpty());
        }

        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            f.outputs.get(1).startDelivery = new CompletableFuture<>();
            f.start();
            var token = f.token();
            f.ack(0);
            f.ack(1);
            f.room.scenario.matchTimers.rejectSchedule = true;
            f.outputs.get(1).startDelivery.complete(null);

            assertTrue(f.room.scenario.matches.current(token).isEmpty());
            assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1));
            assertTrue(logs.text().contains("match.command.failed"));
        }
    }

    @Test
    void ignoresStaleWriteFailuresAndClosesOnlyCapturedConnectionsWhenCommandDispatchIsUnavailable() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            f.outputs.getFirst().bootstrapFailure = new IllegalStateException("credential-secret");
            f.start();

            assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1));
            assertTrue(logs.text().contains("match.bootstrap.delivery.failed"));
            assertFalse(logs.text().contains("credential-secret"));
        }

        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            var delayed = f.outputs.get(1);
            delayed.startDelivery = new CompletableFuture<>();
            f.start();
            var token = f.token();
            f.room.scenario.rooms.terminateMatch(token, RoomMatchEndReason.GIVE_UP);
            delayed.startDelivery.completeExceptionally(new IllegalStateException("old write failure"));

            assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
        }

        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            f.room.scenario.matchTimers.rejectExecute = true;
            f.start();
            assertTrue(f.outputs.stream().allMatch(output -> !output.closed.isEmpty()));
            assertTrue(logs.text().contains("MATCH_SCHEDULER_UNAVAILABLE"));
            f.room.scenario.rooms.release(f.room.owner, "MATCH_SCHEDULER_UNAVAILABLE");
            assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
        }
    }

    @Test
    void neverPublishesWithoutCapturedOutputsAndHandlesExplicitCancellationBeforePublication() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            var rooms = f.room.scenario.rooms;
            var matches = f.room.scenario.matches;
            var token = rooms.reserveMatch(f.room.owner).token();
            rooms.commitMatch(token).toCompletableFuture().join();
            matches.publish(token);

            assertTrue(f.outputs.getFirst().starts.isEmpty());
            assertEquals(RoomStartOutcome.REJECTED, rooms.requestStart(f.room.players.get(1)).outcome());
        }

        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            var rooms = f.room.scenario.rooms;
            var token = rooms.reserveMatch(f.room.owner).token();
            assertTrue(f.room.scenario.matches.activate(token, rooms.matchCallbacks()));
            rooms.commitMatch(token).toCompletableFuture().join();
            var result = rooms.terminateMatch(token, RoomMatchEndReason.GIVE_UP);

            assertEquals(RoomMatchTerminationMode.DISCONNECT, result.termination().mode());
            assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1));
        }
    }

}

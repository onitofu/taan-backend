package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.MatchBootstrapFixture;
import taan.online.MatchRoomFixture;
import taan.online.RoomScenario;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMembership;
import taan.online.session.model.SessionId;

class MatchAsyncLifecycleTest {
    @Test
    void queuesResultsBeforeDepartureNotificationsAndRejectsTheOldTimer() {
        var tasks = new ArrayDeque<Runnable>();

        try (var f = new MatchRoomFixture(new RoomScenario(tasks::addLast, 64), 2, RoomMode.ITEM)) {
            var matches = f.scenario.matches;
            var owner = new TestMatchOutput();
            var guest = new TestMatchOutput();
            matches.subscribe(f.owner, owner);
            matches.subscribe(f.players.get(1), guest);
            var start = f.scenario.start.execute(f.owner);

            assertFalse(start.delivery().toCompletableFuture().isDone());
            assertTrue(owner.starts.isEmpty());
            run(tasks);
            var context = matches.context(f.owner).roomContext();
            var snapshot = matches.current(context.token()).orElseThrow();

            for (var participant : snapshot.participants()) {
                var member = matches.context(participant.sessionId()).roomContext();
                matches.loadingDone(
                    member,
                    participant.slotIndex(),
                    participant.spawn().settledX(),
                    participant.spawn().settledY()
                );
            }

            run(tasks);
            assertEquals(MatchPhase.PREVIEW, matches.current(context.token()).orElseThrow().phase());
            var oldTimer = f.scenario.matchTimers.commands.getFirst();
            f.outputs.forEach(output -> output.onReady = () -> assertEquals(1, guest.ends));
            f.scenario.rooms.release(f.owner, "PEER_CLOSED");

            assertEquals(0, guest.ends);
            assertTrue(matches.current(context.token()).isEmpty());
            run(tasks);

            assertEquals(1, guest.ends);
            assertEquals(0, owner.ends);
            assertEquals(RoomState.WAITING, f.scenario.rooms.current(f.players.get(1)).orElseThrow().state());
            oldTimer.run();
            run(tasks);

            assertEquals(1, guest.ends);
            assertTrue(guest.closed.isEmpty());
        } finally {
            run(tasks);
        }
    }

    @Test
    void cancelsQueuedRequestsAfterDisconnectAndRechecksSessionIdentityBeforeProcessing() {
        var tasks = new ArrayDeque<Runnable>();

        try (var f = new MatchRoomFixture(new RoomScenario(tasks::addLast, 64), 2, RoomMode.ITEM)) {
            var matches = f.scenario.matches;
            matches.subscribe(f.owner, new TestMatchOutput());
            matches.subscribe(f.players.get(1), new TestMatchOutput());
            f.scenario.start.execute(f.owner);
            run(tasks);
            var context = matches.context(f.owner).roomContext();
            var membership = context.membership();
            var wrong = new RoomMatchContext(
                new RoomMembership(
                    membership.sessionId(),
                    membership.roomId(),
                    membership.generation(),
                    membership.channelId(),
                    UUID.randomUUID()
                ),
                context.token()
            );
            var rejected = matches.terminate(wrong, RoomMatchEndReason.GIVE_UP);
            var rejectedCommand = matches.submit(
                new TerminateMatchCommand(MatchCommandContext.from(wrong), RoomMatchEndReason.GIVE_UP)
            );
            run(tasks);

            assertNull(rejected.toCompletableFuture().join().termination());
            assertFalse(rejectedCommand.toCompletableFuture().join());
            var pending = matches.reportEnemies(context);
            f.scenario.sessions.close(new SessionId(f.owner));

            assertNull(matches.context(f.owner));
            run(tasks);
            assertFalse(pending.toCompletableFuture().join());
            var cancelled = matches.loadingDone(context, 0, 97, 685);
            f.scenario.rooms.release(f.owner, "PEER_CLOSED");
            run(tasks);

            assertNull(cancelled.toCompletableFuture().join());
            assertTrue(matches.current(context.token()).isEmpty());
            assertTrue(f.scenario.matchTimers.deadlines.isEmpty());
        } finally {
            run(tasks);
        }
    }

    @Test
    void rollsBackAQueuedStartWhenTheRoomIsClosedBeforeItCanRun() {
        var tasks = new ArrayDeque<Runnable>();

        try (var f = new MatchRoomFixture(new RoomScenario(tasks::addLast, 64), 2, RoomMode.ITEM)) {
            var matches = f.scenario.matches;
            var output = new TestMatchOutput();
            matches.subscribe(f.owner, output);
            matches.subscribe(f.players.get(1), new TestMatchOutput());
            var start = f.scenario.start.execute(f.owner);
            var token = matches.context(f.owner).token();
            f.scenario.rooms.close();
            assertFalse(f.scenario.rooms.matchCallbacks().startCurrent(token));
            run(tasks);

            assertTrue(start.delivery().toCompletableFuture().isDone());
            assertTrue(matches.current(token).isEmpty());
            assertTrue(output.starts.isEmpty());
            assertEquals(List.of("MATCH_SERVER_STOPPING"), output.closed);
        } finally {
            run(tasks);
        }
    }

    @Test
    void rechecksAClosedSessionWhenItsPreviouslyAcceptedStartReachesTheOwner() {
        var tasks = new ArrayDeque<Runnable>();

        try (var f = new MatchRoomFixture(new RoomScenario(tasks::addLast, 64), 2, RoomMode.ITEM)) {
            var matches = f.scenario.matches;
            var output = new TestMatchOutput();
            matches.subscribe(f.owner, output);
            matches.subscribe(f.players.get(1), new TestMatchOutput());
            f.scenario.start.execute(f.owner);
            var token = matches.context(f.owner).token();
            f.scenario.sessions.close(new SessionId(f.owner));
            run(tasks);

            assertTrue(matches.current(token).isEmpty());
            assertTrue(output.starts.isEmpty());
            assertTrue(output.closed.isEmpty());
        } finally {
            run(tasks);
        }
    }

    @Test
    void doesNotApplyAnOldRoomCompletionToAReplacementWithTheSameNumber() throws Exception {
        var tasks = new ArrayDeque<Runnable>();

        try (var f = new MatchRoomFixture(new RoomScenario(tasks::addLast, 64), 2, RoomMode.ITEM)) {
            var matches = f.scenario.matches;
            matches.subscribe(f.owner, new TestMatchOutput());
            matches.subscribe(f.players.get(1), new TestMatchOutput());
            f.scenario.start.execute(f.owner);
            run(tasks);
            var token = matches.context(f.owner).token();
            var rooms = rooms(f.scenario.rooms);
            var original = rooms.get(token.roomId());
            f.scenario.rooms.terminateMatch(token, RoomMatchEndReason.GIVE_UP);
            var replacement = new Room(
                original.id(),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                null,
                original.owner()
            );
            rooms.put(original.id(), replacement);

            try {
                run(tasks);
                assertEquals(RoomState.WAITING, replacement.state());
                assertEquals(token.matchId(), original.matchId());
            } finally {
                rooms.put(original.id(), original);
                original.rollbackMatch(token.matchId());
            }
        } finally {
            run(tasks);
        }
    }

    @Test
    void handlesGameplayWithoutTakingEitherTheRoomOrRegistryMonitor() throws Exception {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.start();
            var matches = f.room.scenario.matches;
            var context = f.context(0);

            synchronized (f.room.scenario.rooms) {
                synchronized (matches) {
                    var report = CompletableFuture.supplyAsync(
                        () -> matches.reportEnemies(context).toCompletableFuture().join()
                    );
                    assertTrue(report.get(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    private Map<RoomId, Room> rooms(RoomRegistry registry) throws Exception {
        var field = RoomRegistry.class.getDeclaredField("rooms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rooms = (Map<RoomId, Room>) field.get(registry);
        return rooms;
    }

    private void run(ArrayDeque<Runnable> tasks) {
        while (!tasks.isEmpty()) {
            tasks.removeFirst().run();
        }
    }
}

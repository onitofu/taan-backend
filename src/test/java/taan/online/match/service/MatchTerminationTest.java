package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.logging.LogContext;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.MatchTerminalFixture;
import taan.online.match.model.MatchPhase;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationMode;

class MatchTerminationTest {
    @Test
    void completesEveryModeAndReasonExactlyOnceWithoutRetainingAnActiveMatch() {
        for (var mode : RoomMode.values()) {
            for (int count : new int[]{2, 8}) {
                for (var phase : new MatchPhase[]{MatchPhase.RESERVED, MatchPhase.LOADING, MatchPhase.PREVIEW}) {
                    for (var reason : RoomMatchEndReason.values()) {
                        try (var f = new MatchTerminalFixture(count, mode, phase)) {
                            var rooms = f.room.scenario.rooms;
                            var result = rooms.terminateMatch(f.token, reason);
                            var expected = reason == RoomMatchEndReason.SERVER_STOPPING
                                ? RoomMatchTerminationMode.DISCONNECT
                                : phase == MatchPhase.RESERVED ? RoomMatchTerminationMode.ROLLBACK
                                    : phase == MatchPhase.PREVIEW ? RoomMatchTerminationMode.RETURN_TO_ROOM
                                        : RoomMatchTerminationMode.DISCONNECT;

                            assertEquals(expected, result.termination().mode());
                            assertEquals(reason, result.termination().reason());
                            assertTrue(result.delivery().toCompletableFuture().isDone());
                            assertTrue(f.room.scenario.matches.current(f.token).isEmpty());
                            assertNull(rooms.terminateMatch(f.token, reason).termination());
                            assertEquals(RoomState.WAITING, rooms.current(f.room.owner).orElseThrow().state());
                            assertTrue(
                                rooms.current(f.room.owner).orElseThrow().slots().stream()
                                    .noneMatch(slot -> slot.state() == RoomSlotState.READY)
                            );

                            for (var output : f.outputs) {
                                assertEquals(expected == RoomMatchTerminationMode.RETURN_TO_ROOM ? 1 : 0, output.ends);
                                assertEquals(
                                    expected == RoomMatchTerminationMode.DISCONNECT ? 1 : 0,
                                    output.closed.size()
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void queuesResultsBeforeReadinessUpdatesAndDoesNotWaitForOtherRecipients() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW)) {
            var rooms = f.room.scenario.rooms;
            var owner = f.room.owner;
            var context = rooms.matchContext(owner);
            f.outputs.get(1).delivery = new CompletableFuture<>();
            f.room.outputs.forEach(
                output -> output.onReady = () -> {
                    assertEquals(1, f.outputs.get(0).ends);
                    assertEquals(1, f.outputs.get(1).ends);
                }
            );
            var result = new TerminateMatchAction(rooms).execute(owner, context);

            assertTrue(result.delivery().toCompletableFuture().isDone());
            assertFalse(f.outputs.get(1).delivery.isDone());
            assertEquals(f.token, rooms.matchContext(owner).token());
            assertTrue(new ConsumeMatchResultAction(rooms, f.room.scenario.matches).execute(owner, context));
            assertNull(new TerminateMatchAction(rooms).execute(owner, context).termination());
        }
    }

    @Test
    void waitsForTheSendersDeliveryAndClosesOnlyItsBrokenOutputWithoutLeakingSecrets() {
        for (boolean synchronous : new boolean[]{false, true}) {
            try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW); var logs = new LogCapture()) {
                var owner = f.room.owner;
                var actor = f.outputs.getFirst();
                var peer = f.outputs.get(1);
                actor.delivery = new CompletableFuture<>();

                if (synchronous) {
                    actor.failure = new IllegalStateException("private@example.test credential-secret");
                }

                var result = f.room.scenario.rooms.terminateMatch(
                    f.room.scenario.rooms.matchContext(owner),
                    RoomMatchEndReason.GIVE_UP
                );

                if (!synchronous) {
                    assertFalse(result.delivery().toCompletableFuture().isDone());
                    actor.delivery.completeExceptionally(
                        new IllegalStateException("private@example.test credential-secret")
                    );
                }

                assertTrue(result.delivery().toCompletableFuture().isDone());
                assertEquals(1, actor.closed.size());
                assertTrue(peer.closed.isEmpty());
                assertEquals(1, peer.ends);
                assertEquals(
                    1,
                    logs.events().stream().filter(event -> event.getLevel().toString().equals("ERROR")).count()
                );
                assertTrue(logs.text().contains("match.terminal.delivery.failed"));
                assertFalse(logs.text().contains("private@example.test"));
                assertFalse(logs.text().contains("credential-secret"));
                assertFalse(logs.text().contains("Hero"));
                f.room.scenario.rooms.release(owner, "WRITE_FAILED");
                f.room.scenario.rooms.release(owner, "WRITE_FAILED");

                assertEquals(1, peer.ends);
                assertEquals(
                    f.room.players.get(1),
                    f.room.scenario.rooms.current(f.room.players.get(1))
                        .orElseThrow().ownerSessionId()
                );
            }
        }
    }

    @Test
    void retainsOperationContextWhenAnEndGameDeliveryFailsOnAnotherThread() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW); var logs = new LogCapture()) {
            var actor = f.outputs.getFirst();
            actor.delivery = new CompletableFuture<>();
            RoomMatchTerminationResult result;

            try (var scope = new LogContext("connection", "terminal-operation", f.room.owner.toString())) {
                result = f.room.scenario.rooms.terminateMatch(f.token, RoomMatchEndReason.GIVE_UP);
            }

            CompletableFuture.runAsync(() -> actor.delivery.completeExceptionally(new IllegalStateException())).join();
            result.delivery().toCompletableFuture().join();
            var event = logs.events().stream().filter(value -> value.getLevel().toString().equals("ERROR"))
                .findFirst().orElseThrow();

            assertEquals("terminal-operation", event.getMDCPropertyMap().get("operationId"));
            assertEquals(f.room.owner.toString(), event.getMDCPropertyMap().get("sessionId"));
            assertEquals(1, actor.closed.size());
            assertTrue(f.outputs.get(1).closed.isEmpty());
        }
    }

}

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
import taan.online.match.model.MatchPhase;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomStartOutcome;

class MatchBootstrapTest {
    @Test
    void startsEveryModeWithAllParticipantsAndEmitsOneTurnOnlyAfterEveryAcknowledgement() {
        for (var mode : RoomMode.values()) {
            for (int count : new int[]{2, 8}) {
                try (var f = new MatchBootstrapFixture(count, mode)) {
                    assertEquals(RoomStartOutcome.STARTED, f.start().outcome());
                    var token = f.token();
                    var frozen = f.snapshot();

                    assertEquals(MatchPhase.LOADING, frozen.phase());
                    assertEquals(0, frozen.initialWindRaw());
                    assertTrue(
                        frozen.participants().stream().anyMatch(value -> value.slotIndex() == frozen.firstTurnSlot())
                    );
                    assertEquals(RoomStartOutcome.ALREADY_STARTED, f.start().outcome());
                    assertTrue(
                        f.outputs.stream().allMatch(output -> output.starts.size() == 1 && output.turns.isEmpty())
                    );

                    for (int index = 0; index < count; index++) {
                        f.ack(index);
                        f.ack(index);
                        assertEquals(
                            index == count - 1 ? MatchPhase.PREVIEW : MatchPhase.LOADING,
                            f.snapshot().phase()
                        );
                    }

                    assertTrue(
                        f.outputs.stream().allMatch(output -> output.starts.size() == 1 && output.turns.size() == 1)
                    );
                    assertEquals(1, f.room.scenario.matchTimers.deadlines.size());
                    assertEquals(20_000_000_000L, f.snapshot().deadlineNanos());
                    f.room.scenario.rooms.terminateMatch(token, RoomMatchEndReason.GIVE_UP);

                    assertTrue(f.outputs.stream().allMatch(output -> output.ends == 1));
                    assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
                    assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(f.room.owner).orElseThrow().state());
                }
            }
        }
    }

    @Test
    void waitsForStartDeliveryAndFirstTurnDeliveryWithoutBlockingAnEarlyLoadingRequest() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.outputs.get(1).startDelivery = new CompletableFuture<>();
            f.outputs.get(1).turnDelivery = new CompletableFuture<>();
            var result = f.start();
            f.ack(0);
            f.ack(1);

            assertTrue(result.delivery().toCompletableFuture().isDone());
            assertEquals(MatchPhase.LOADING, f.snapshot().phase());
            assertTrue(f.outputs.getFirst().turns.isEmpty());
            f.room.scenario.matchTimers.now = 9_000_000_000L;
            f.outputs.get(1).startDelivery.complete(null);

            assertEquals(MatchPhase.SYNCHRONIZING, f.snapshot().phase());
            assertEquals(30_000_000_000L, f.snapshot().deadlineNanos());
            assertEquals(1, f.outputs.getFirst().turns.size());
            assertTrue(
                f.room.scenario.rooms.matchAccess().confirmPosition(
                    f.context(1),
                    f.snapshot().participants().get(1).slotIndex(),
                    f.snapshot().placements().get(f.room.players.get(1)).settledX(),
                    f.snapshot().placements().get(f.room.players.get(1)).settledY(),
                    f.snapshot().participants().get(1).facing()
                )
            );
            f.outputs.get(1).turnDelivery.complete(null);

            assertEquals(MatchPhase.PREVIEW, f.snapshot().phase());
            assertEquals(29_000_000_000L, f.snapshot().deadlineNanos());
        }
    }

    @Test
    void fixesTheFirstValidPlacementAndAcceptsAlternateAndNeutralSpawnsWithoutTrustingOtherPositions() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.start();
            var rooms = f.room.scenario.rooms.matchAccess();
            var owner = f.context(0);
            rooms.loadingDone(owner, 2, 1110, 685);
            rooms.loadingDone(owner, 0, 500, 500);
            rooms.loadingDone(owner, 0, 1110, 685);

            assertTrue(f.snapshot().placements().isEmpty());
            rooms.loadingDone(owner, 0, 336, 676);
            rooms.loadingDone(owner, 0, 97, 685);

            assertEquals(336, f.snapshot().placements().get(f.room.owner).settledX());
            rooms.loadingDone(f.context(1), 2, 847, 658);

            assertEquals(MatchPhase.PREVIEW, f.snapshot().phase());
            assertTrue(rooms.confirmPosition(owner, 0, 336, 676, 0));
            assertFalse(rooms.confirmPosition(owner, 0, 336, 676, 1));
            assertFalse(rooms.confirmPosition(owner, 2, 847, 658, 1));
            assertFalse(rooms.confirmPosition(owner, 0, 500, 500, 0));
            assertEquals(1, f.outputs.getFirst().turns.size());
        }
    }

    @Test
    void appliesOneMonotonicDeadlineAndRejectsOldCompletionAndTimerCommands() {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.start();
            var oldTimer = f.room.scenario.matchTimers.commands.getFirst();
            var oldProgress = f.room.scenario.matches.progress(f.token());
            var oldToken = f.token();
            f.room.scenario.matchTimers.advanceSeconds(29);
            f.ack(0);
            f.ack(0);
            f.room.scenario.matchTimers.advanceSeconds(1);

            assertTrue(f.room.scenario.matches.current(oldToken).isEmpty());
            assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1 && output.ends == 0));
            f.room.scenario.rooms.setReady(f.room.players.get(1), true);
            f.start();
            var next = f.token();
            oldTimer.run();

            assertTrue(f.room.scenario.matches.current(next).isPresent());
            assertFalse(oldToken.equals(next));
            f.ack(0);
            f.ack(1);
            f.room.scenario.matchTimers.advanceSeconds(20);

            assertTrue(f.room.scenario.matches.current(next).isEmpty());
            assertTrue(f.outputs.stream().allMatch(output -> output.ends == 1));
            assertNull(f.room.scenario.matches.deliveryCompleted(oldProgress, UUID.randomUUID()));
        }
    }

    @Test
    void closesAllParticipantsOnPartialBootstrapDeliveryFailureWithoutLeakingExceptionMessages() {
        for (boolean firstTurn : new boolean[]{false, true}) {
            try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
                var broken = f.outputs.get(1);

                if (!firstTurn) {
                    broken.startDelivery = new CompletableFuture<>();
                } else {
                    broken.turnDelivery = new CompletableFuture<>();
                }

                f.start();
                var token = f.token();

                if (firstTurn) {
                    f.ack(0);
                    f.ack(1);
                    broken.turnDelivery.completeExceptionally(
                        new IllegalStateException("secret@example.test verifier-secret")
                    );
                } else {
                    broken.startDelivery.completeExceptionally(
                        new IllegalStateException("secret@example.test verifier-secret")
                    );
                }

                assertTrue(f.room.scenario.matches.current(token).isEmpty());
                assertTrue(f.outputs.stream().allMatch(output -> output.closed.size() == 1 && output.ends == 0));
                assertTrue(logs.text().contains("match.bootstrap.delivery.failed"));
                assertFalse(logs.text().contains("secret@example.test"));
                assertFalse(logs.text().contains("verifier-secret"));
            }
        }
    }
}

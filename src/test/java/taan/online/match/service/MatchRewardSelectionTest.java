package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.LogCapture;
import taan.online.logging.LogContext;
import taan.online.match.service.model.SelectMatchRewardCommand;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

class MatchRewardSelectionTest {
    @Test
    void serializesContestedSelectionAndReplaysOneChoiceWithoutMovingIt() {
        try (var f = new RewardSelectionFixture(2, 8, true)) {
            var first = f.selections.select(f.command(0, 3)).toCompletableFuture();
            var second = f.selections.select(f.command(1, 3)).toCompletableFuture();

            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertEquals(1, f.work.size());
            f.work.removeFirst().run();
            first.join();
            second.join();

            assertEquals(1, f.output(0).selections.size());
            assertEquals(2, f.output(1).selections.size());
            assertTrue(f.output(1).selections.getLast().alreadyOpen());
            assertEquals(3, f.output(1).selections.getLast().cardIndex());

            var random = f.selections.select(f.command(1, null));
            f.work.removeFirst().run();
            random.toCompletableFuture().join();

            assertEquals(0, f.output(1).selections.getLast().cardIndex());
            assertFalse(f.output(1).selections.getLast().alreadyOpen());
            int peerCount = f.output(0).selections.size();
            var repeated = f.selections.select(f.command(1, 7));
            f.work.removeFirst().run();
            repeated.toCompletableFuture().join();

            assertEquals(0, f.output(1).selections.getLast().cardIndex());
            assertEquals(peerCount, f.output(0).selections.size());
        }
    }

    @Test
    void fencesForeignSlotsAdmissionsGenerationsAndLateCommandsWithoutSendingAnything() {
        try (var f = new RewardSelectionFixture(2, 8, false)) {
            var valid = f.command(0, 0);
            var member = valid.context().membership();
            var wrong = new RoomMembership(
                member.sessionId(),
                member.roomId(),
                member.generation(),
                member.channelId(),
                UUID.randomUUID()
            );
            var token = valid.context().token();

            for (var command : List.of(
                new SelectMatchRewardCommand(UUID.randomUUID(), valid.context(), valid.slotIndex(), 0),
                new SelectMatchRewardCommand(valid.sessionId(), null, valid.slotIndex(), 0),
                new SelectMatchRewardCommand(
                    valid.sessionId(),
                    new RoomMatchContext(
                        wrong,
                        token
                    ),
                    valid.slotIndex(),
                    0
                ),
                new SelectMatchRewardCommand(
                    valid.sessionId(),
                    new RoomMatchContext(
                        member,
                        new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID())
                    ),
                    valid.slotIndex(),
                    0
                ),
                new SelectMatchRewardCommand(valid.sessionId(), valid.context(), 7, 0),
                new SelectMatchRewardCommand(valid.sessionId(), valid.context(), valid.slotIndex(), -1),
                new SelectMatchRewardCommand(valid.sessionId(), valid.context(), valid.slotIndex(), 8)
            )) {
                f.selections.select(command).toCompletableFuture().join();
            }

            assertTrue(f.output(0).selections.isEmpty());
            assertTrue(f.output(1).selections.isEmpty());
            f.selections.release(wrong);
            assertEquals(member, f.selections.receipt(valid.sessionId()).membership());
            f.selections.remove(valid.sessionId());
            f.selections.select(valid).toCompletableFuture().join();

            assertNull(f.selections.receipt(valid.sessionId()));
            assertTrue(f.output(0).selections.isEmpty());
        }
    }

    @Test
    void fencesReplacedReceiptsDuringPublicationAndRecipientDelivery() {
        for (boolean newToken : new boolean[]{true, false}) {
            try (var f = new RewardSelectionFixture(2, 8, false)) {
                var member = f.members.getFirst();
                var original = f.terminal.token();
                var token = newToken ? new RoomMatchToken(original.roomId(), original.generation(), UUID.randomUUID())
                    : original;
                var replacement = newToken ? member : new RoomMembership(
                    member.sessionId(),
                    member.roomId(),
                    member.generation(),
                    member.channelId(),
                    UUID.randomUUID()
                );
                f.selections.reserve(token, List.of(replacement));
                f.selections.select(f.command(1, 1)).toCompletableFuture().join();

                assertTrue(f.output(0).selections.isEmpty());
                assertEquals(1, f.output(1).selections.size());
                f.selections.publish(f.terminal, f.fixture.snapshot().participants(), f.outputs);
                f.selections.select(f.command(1, 2)).toCompletableFuture().join();

                assertTrue(f.output(0).selections.isEmpty());
                assertEquals(2, f.output(1).selections.size());
                assertEquals(2, f.output(1).selections.getLast().cardIndex());
            }
        }
    }

    @Test
    void cleansUnpublishedAndPublishedResultsCancelsQueuedWorkAndBoundsTheQueue() {
        try (var f = new RewardSelectionFixture(2, 1, true); var logs = new LogCapture()) {
            var pending = f.selections.select(f.command(0, 0)).toCompletableFuture();
            var overflow = f.selections.select(f.command(1, 1)).toCompletableFuture();

            assertTrue(overflow.isDone());
            assertTrue(logs.text().contains("RESULT_QUEUE_FULL"));
            f.members.forEach(f.selections::release);

            assertTrue(pending.isDone());
            f.work.removeFirst().run();
            assertTrue(f.output(0).selections.isEmpty());
            f.selections.publish(f.terminal, f.fixture.snapshot().participants(), f.outputs);
            f.selections.select(f.command(0, 0)).toCompletableFuture().join();
            assertTrue(f.output(0).selections.isEmpty());
        }
    }

    @Test
    void preservesTheInitiatingLogContextForDelayedDeliveryFailuresAndRestoresTheCompletingThreadsContext() {
        try (var f = new RewardSelectionFixture(2, 8, false); var logs = new LogCapture()) {
            var delivery = new CompletableFuture<Void>();
            f.output(1).selectionDelivery = delivery;
            CompletableFuture<Void> selected;

            try (var context = new LogContext("reward-connection", "reward-operation")) {
                selected = f.selections.select(f.command(0, 1)).toCompletableFuture();
            }

            assertFalse(selected.isDone());
            MDC.put("operationId", "other-operation");

            try {
                delivery.completeExceptionally(new IllegalStateException("private-reward-error"));
                selected.join();

                var event = logs.events().stream().filter(e -> e.getFormattedMessage()
                    .equals("Result card delivery failed")).findFirst().orElseThrow();
                assertEquals("reward-operation", event.getMDCPropertyMap().get("operationId"));
                assertEquals("other-operation", MDC.get("operationId"));
                assertFalse(logs.text().contains("private-reward-error"));
            } finally {
                MDC.clear();
            }
        }
    }

    @Test
    void containsRecipientFailuresAndSkipsDepartedPeers() {
        for (boolean synchronous : new boolean[]{true, false}) {
            try (var f = new RewardSelectionFixture(2, 8, false); var logs = new LogCapture()) {
                if (synchronous) {
                    f.output(1).selectionFailure = new IllegalStateException("private-selection-value");
                } else {
                    f.output(1).selectionDelivery = CompletableFuture.failedFuture(
                        new IllegalStateException("private-selection-value")
                    );
                }

                f.selections.select(f.command(0, 1)).toCompletableFuture().join();

                assertEquals(1, f.output(0).selections.size());
                assertEquals(List.of("RESULT_SELECTION_DELIVERY_FAILED"), f.output(1).closed);
                assertTrue(logs.text().contains("match.result.selection.failed"));
                assertFalse(logs.text().contains("private-selection-value"));
            }
        }

        try (var f = new RewardSelectionFixture(2, 8, false)) {
            f.output(0).onSelection = () -> f.selections.remove(f.members.get(1).sessionId());
            f.selections.select(f.command(0, 1)).toCompletableFuture().join();

            assertEquals(1, f.output(0).selections.size());
            assertTrue(f.output(1).selections.isEmpty());
        }
    }
}

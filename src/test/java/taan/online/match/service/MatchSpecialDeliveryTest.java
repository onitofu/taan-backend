package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchSpecialDeliveryTest {
    @Test
    void handlesSynchronousAndAsynchronousActivationDeliveryFailureOnceWithoutLeakingMessages() throws Exception {
        for (boolean synchronous : new boolean[]{true, false}) {
            try (var e = new EnergyRuntimeFixture(); var logs = new LogCapture()) {
                e.charge();
                if (synchronous) {
                    e.f.owner.motionFailure = new IllegalStateException("credential-secret");
                } else {
                    e.f.owner.delivery = CompletableFuture.failedFuture(new IllegalStateException("credential-secret"));
                    e.f.guest.delivery = e.f.owner.delivery;
                }
                assertTrue(e.activate(e.use()));
                assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, e.f.callbacks.reason);
                assertEquals(1, logs.text().split("event=\"match.item.delivery.failed\"", -1).length - 1);
                assertFalse(logs.text().contains("credential-secret"));
                assertTrue(e.f.match.attacks().isEmpty());
            }
        }
    }

    @Test
    void preservesCommittedEnergyWhenSpendingCannotBeDeliveredAndDropsStaleRecipients() throws Exception {
        for (boolean synchronous : new boolean[]{true, false}) {
            try (var e = new EnergyRuntimeFixture()) {
                e.charge();
                assertTrue(e.activate(e.use()));
                assertTrue(e.record(e.spend(List.of())));
                if (synchronous) {
                    e.f.owner.motionFailure = new IllegalStateException("spend-failure");
                } else {
                    e.f.owner.delivery = CompletableFuture.failedFuture(new IllegalStateException("spend-failure"));
                }
                var result = e.f.runtime.applyScenario(e.shot(true, 10));
                e.f.run();
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome());
                assertEquals(10, e.snapshot().actors().get(e.f.room.owner).energy());
                assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, e.f.callbacks.reason);
            }
        }

        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            e.f.owner.onMovement = () -> e.f.admitted = false;
            assertTrue(e.activate(e.use()));
            assertEquals(1, e.f.owner.specials.size());
            assertTrue(e.f.guest.specials.isEmpty());
        }
    }

    @Test
    void ignoresLateAndDuplicateDeliveryFailuresAndRevalidatesSpendRecipients() throws Exception {
        try (var e = new EnergyRuntimeFixture(); var logs = new LogCapture()) {
            e.charge();
            var pending = new CompletableFuture<Void>();
            e.f.owner.delivery = pending;
            e.f.guest.delivery = pending;
            assertTrue(e.activate(e.use()));
            e.f.runtime.stop();
            pending.completeExceptionally(new IllegalStateException("late-secret"));
            e.f.run();
            assertFalse(logs.text().contains("match.item.delivery.failed"));
        }

        try (var e = new EnergyRuntimeFixture(); var logs = new LogCapture()) {
            e.charge();
            e.f.callbacks.rejectTermination = true;
            var failed = CompletableFuture.<Void>failedFuture(new IllegalStateException("duplicate-secret"));
            e.f.owner.delivery = failed;
            e.f.guest.delivery = failed;
            assertTrue(e.activate(e.use()));
            assertEquals(1, logs.text().split("event=\"match.item.delivery.failed\"", -1).length - 1);
            assertFalse(logs.text().contains("duplicate-secret"));
        }

        try (var e = new EnergyRuntimeFixture()) {
            assertTrue(e.record(e.spend(List.of())));
            e.f.owner.onSpend = () -> e.f.admitted = false;
            var applied = e.f.runtime.applyScenario(e.shot(false, 0));
            e.f.run();
            assertTrue(applied.join().committed().isPresent());
            assertEquals(1, e.f.owner.spends.size());
            assertTrue(e.f.guest.spends.isEmpty());
        }
    }

    @Test
    void cancelsSafelyWhenAnAsynchronousItemDeliveryFailsAgainstAFullCommandQueue() throws Exception {
        try (var e = new EnergyRuntimeFixture(); var logs = new LogCapture()) {
            e.charge();
            var pending = new CompletableFuture<Void>();
            e.f.owner.delivery = pending;
            assertTrue(e.activate(e.use()));

            for (int index = 0; index < 8; index++) {
                e.f.runtime.turnWork(() -> true);
            }

            pending.completeExceptionally(new IllegalStateException("delivery-secret"));
            e.f.run();
            assertEquals(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED, e.f.callbacks.reason);
            assertEquals(1, logs.text().split("event=\"match.item.delivery.failed\"", -1).length - 1);
            assertFalse(logs.text().contains("delivery-secret"));
        }
    }
}

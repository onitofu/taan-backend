package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.bootstrap.BoundedMatchExecutor;

class MatchMailboxTest {
    @Test
    void preservesFifoAndCompletesEachCommandWithItsOwnResult() {
        var scheduled = new ArrayDeque<Runnable>();
        var mailbox = new MatchMailbox(scheduled::addLast, 3);
        var order = new ArrayList<Integer>();
        var first = mailbox.submit(() -> order.add(1), () -> { });
        var second = mailbox.submit(
            () -> {
                order.add(2);
                return false;
            },
            () -> { }
        );

        assertEquals(1, scheduled.size());
        assertFalse(first.isDone());
        assertFalse(second.isDone());
        scheduled.removeFirst().run();

        assertEquals(List.of(1, 2), order);
        assertTrue(first.join());
        assertFalse(second.join());
        assertFalse(mailbox.submit(() -> true, () -> { }).isDone());
        scheduled.removeFirst().run();
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void reservesTerminalCapacityCancelsQueuedCommandsAndRejectsFurtherWork() {
        var scheduled = new ArrayDeque<Runnable>();
        var mailbox = new MatchMailbox(scheduled::addLast, 1);
        var calls = new AtomicInteger();
        var rejected = new AtomicInteger();
        var queued = mailbox.submit(
            () -> {
                calls.incrementAndGet();
                return true;
            },
            rejected::incrementAndGet
        );
        var overflow = mailbox.submit(() -> true, rejected::incrementAndGet);

        assertFalse(overflow.join());
        assertEquals(1, rejected.get());
        var terminal = mailbox.terminate(
            () -> {
                calls.addAndGet(10);
                return true;
            }
        );
        assertSame(terminal, mailbox.terminate(() -> false));
        assertFalse(queued.join());
        assertFalse(mailbox.submit(() -> true, rejected::incrementAndGet).join());
        scheduled.removeFirst().run();

        assertTrue(terminal.join());
        assertEquals(10, calls.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void handlesExecutorRejectionAndStillRunsTerminalCleanupExactlyOnce() {
        var mailbox = new MatchMailbox(
            command -> {
                throw new RejectedExecutionException();
            },
            1
        );
        var rejected = new AtomicInteger();
        var cleanup = new AtomicInteger();

        assertFalse(mailbox.submit(() -> true, rejected::incrementAndGet).join());
        assertEquals(1, rejected.get());
        var terminal = mailbox.terminate(
            () -> {
                cleanup.incrementAndGet();
                return true;
            }
        );

        assertTrue(terminal.join());
        assertSame(terminal, mailbox.terminate(() -> false));
        assertEquals(1, cleanup.get());
        assertThrows(IllegalArgumentException.class, () -> new MatchMailbox(Runnable::run, 0));
    }

    @Test
    void propagatesFailuresAndLoggingContextWithoutPreventingTheNextCommand() {
        var tasks = new ArrayDeque<Runnable>();
        var mailbox = new MatchMailbox(tasks::addLast, 2);

        try {
            MDC.put("operationId", "first");
            var failed = mailbox.submit(
                () -> {
                    assertEquals("first", MDC.get("operationId"));
                    throw new IllegalArgumentException("command failed");
                },
                () -> { }
            );
            MDC.put("operationId", "second");
            var succeeded = mailbox.submit(
                () -> {
                    assertEquals("second", MDC.get("operationId"));
                    return true;
                },
                () -> { }
            );
            MDC.put("operationId", "worker");
            tasks.removeFirst().run();

            assertThrows(CompletionException.class, failed::join);
            assertTrue(succeeded.join());
            assertEquals("worker", MDC.get("operationId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void serializesOneMatchWhileAnotherMatchMakesProgressOnTheSharedPool() throws Exception {
        try (var executor = new BoundedMatchExecutor(2, 4)) {
            var first = new MatchMailbox(executor, 2);
            var second = new MatchMailbox(executor, 2);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var order = new ArrayList<Integer>();
            var active = new AtomicInteger();
            var running = first.submit(
                () -> {
                    assertEquals(1, active.incrementAndGet());
                    entered.countDown();
                    await(release);
                    order.add(1);
                    active.decrementAndGet();
                    return true;
                },
                () -> { }
            );

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var next = first.submit(
                () -> {
                    assertEquals(0, active.get());
                    order.add(2);
                    return true;
                },
                () -> { }
            );
            var independent = second.submit(() -> true, () -> { });

            try {
                assertTrue(independent.get(5, TimeUnit.SECONDS));
                assertFalse(next.isDone());
            } finally {
                release.countDown();
            }

            assertTrue(running.get(5, TimeUnit.SECONDS));
            assertTrue(next.get(5, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2), order);
            assertTrue(executor.awaitTermination(TimeUnit.SECONDS.toNanos(5)));
        }
    }

    @Test
    void yieldsToOtherMatchesEvenWhenTheFirstMailboxContinuouslyRefills() {
        var scheduled = new ArrayDeque<Runnable>();
        var busy = new MatchMailbox(scheduled::addLast, 2);
        var quiet = new MatchMailbox(scheduled::addLast, 2);
        var calls = new AtomicInteger();
        var operation = new AtomicReference<BooleanSupplier>();
        operation.set(() -> {
            if (calls.incrementAndGet() < 1000) {
                busy.submit(operation.get(), () -> { });
            }
            return true;
        });
        busy.submit(operation.get(), () -> { });
        var other = quiet.submit(() -> true, () -> { });
        scheduled.removeFirst().run();

        assertEquals(32, calls.get());
        assertFalse(other.isDone());
        scheduled.removeFirst().run();
        assertTrue(other.join());
        assertEquals(32, calls.get());

        while (!scheduled.isEmpty()) {
            scheduled.removeFirst().run();
        }
        assertEquals(1000, calls.get());
    }

    @Test
    void fencesGameplayButContinuesBoundedLifecycleWorkOutsideTheMonitor() {
        var scheduled = new ArrayDeque<Runnable>();
        var mailbox = new MatchMailbox(scheduled::addLast, 1);
        var rejected = new AtomicInteger();
        var ordinary = mailbox.submit(() -> true, rejected::incrementAndGet);
        ordinary.thenRun(() -> assertFalse(Thread.holdsLock(mailbox)));
        mailbox.fence();
        mailbox.fence();

        assertFalse(ordinary.join());
        assertFalse(mailbox.submit(() -> true, rejected::incrementAndGet).join());
        var internal = mailbox.control(() -> true, false, rejected::incrementAndGet);
        var overflow = mailbox.control(() -> true, false, rejected::incrementAndGet);
        assertFalse(overflow.join());
        assertEquals(1, rejected.get());
        scheduled.removeFirst().run();
        assertTrue(internal.join());

        mailbox.terminate(() -> true);
        assertFalse(mailbox.control(() -> true, false, rejected::incrementAndGet).join());
        mailbox.fence();
        scheduled.removeFirst().run();
    }

    @Test
    void cancelsPendingWorkOnRescheduleRejectionAndRetainsTerminalCapacity() {
        var scheduled = new ArrayDeque<Runnable>();
        var schedules = new AtomicInteger();
        var rejected = new AtomicInteger();
        var mailbox = new MatchMailbox(command -> {
            if (schedules.incrementAndGet() > 1) {
                throw new RejectedExecutionException();
            }
            scheduled.addLast(command);
        }, 64);
        var results = new ArrayList<CompletableFuture<Boolean>>();

        for (int index = 0; index < 64; index++) {
            results.add(mailbox.submit(() -> true, rejected::incrementAndGet));
        }
        scheduled.removeFirst().run();

        assertEquals(32, results.stream().filter(result -> result.join()).count());
        assertEquals(1, rejected.get());
        assertTrue(scheduled.isEmpty());
        assertTrue(mailbox.terminate(() -> true).join());
    }

    @Test
    void yieldsControlContinuationsAndExecutesReservedCleanupAtTheQuantumBoundary() {
        var scheduled = new ArrayDeque<Runnable>();
        var mailbox = new MatchMailbox(scheduled::addLast, 64);

        for (int index = 0; index < 33; index++) {
            mailbox.control(() -> true, false, () -> { });
        }
        scheduled.removeFirst().run();
        assertEquals(1, scheduled.size());
        scheduled.removeFirst().run();
        assertTrue(scheduled.isEmpty());

        for (int index = 0; index < 32; index++) {
            mailbox.submit(() -> true, () -> { });
        }
        scheduled.removeFirst().run();
        assertTrue(scheduled.isEmpty());

        var ended = new AtomicInteger();

        for (int index = 0; index < 32; index++) {
            int ordinal = index;
            mailbox.submit(() -> {
                if (ordinal == 31) {
                    mailbox.terminate(() -> ended.incrementAndGet() == 1);
                }
                return true;
            }, () -> { });
        }
        scheduled.removeFirst().run();
        assertEquals(0, ended.get());
        scheduled.removeFirst().run();
        assertEquals(1, ended.get());

        try (var invalid = new BoundedMatchExecutor(1, 1, 0)) {
            assertThrows(IllegalArgumentException.class, () -> new MatchMailbox(invalid, 1));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}

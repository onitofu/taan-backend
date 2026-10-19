package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}

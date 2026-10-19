package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;

class MatchShutdownTest {
    @Test
    void preservesInterruptionAndClosesBothWorkerPoolsEvenBeforeStartup() throws Exception {
        var application = new Application(ApplicationConfig.fromEnvironment(TestFixtures.environment()));
        var entered = new CountDownLatch(2);
        var stopped = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        blockWorkers(application, entered, stopped, release);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        Thread.currentThread().interrupt();

        try {
            application.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            release.countDown();
            application.close();
        }

        assertTrue(stopped.await(5, TimeUnit.SECONDS));
    }

    @Test
    void boundsBothWorkerPoolsByOneSharedShutdownDeadline() throws Exception {
        var environment = TestFixtures.environment();
        environment.put("BACKEND_SHUTDOWN_TIMEOUT_SECONDS", "4");
        var application = new Application(ApplicationConfig.fromEnvironment(environment));
        var entered = new CountDownLatch(2);
        var stopped = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        blockWorkers(application, entered, stopped, release);

        try (var logs = new LogCapture()) {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            application.close();

            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            assertTrue(logs.text().contains("server.shutdown.timeout"));
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            release.countDown();
            application.close();
        }
    }

    private void blockWorkers(
        Application application,
        CountDownLatch entered,
        CountDownLatch stopped,
        CountDownLatch release
    ) throws Exception {
        var workerField = Application.class.getDeclaredField("workers");
        workerField.setAccessible(true);
        var workers = (ThreadPoolExecutor) workerField.get(application);
        var matchField = Application.class.getDeclaredField("matchExecutor");
        matchField.setAccessible(true);
        var matches = (BoundedMatchExecutor) matchField.get(application);
        Runnable work = () -> {
            entered.countDown();

            try {
                release.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                stopped.countDown();
            }
        };
        workers.execute(work);
        matches.execute(work);
    }
}

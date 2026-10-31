package taan.online;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public final class TestAwait {
    private TestAwait() {
    }

    public static void until(String description, BooleanSupplier condition) {
        until(description, 5000, condition);
    }

    public static void until(String description, long timeoutMillis, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (!condition.getAsBoolean()) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadline >= 0) {
                throw new AssertionError("Condition did not become true: " + description);
            }

            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }
}

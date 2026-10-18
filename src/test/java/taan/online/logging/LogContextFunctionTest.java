package taan.online.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextFunctionTest {
    @Test
    void propagatesCompletionContextAndRestoresTheWorkerContextEvenAfterFailure() {
        var original = MDC.getCopyOfContextMap();

        try {
            for (boolean captured : new boolean[]{false, true}) {
                for (boolean previous : new boolean[]{false, true}) {
                    for (boolean fail : new boolean[]{false, true}) {
                        MDC.clear();

                        if (captured) {
                            MDC.put("operationId", "match-operation");
                        }

                        var expected = MDC.getCopyOfContextMap();
                        BiFunction<Integer, Integer, Integer> callback = LogContext.propagate(
                            (a, b) -> {
                                assertEquals(expected, MDC.getCopyOfContextMap());

                                if (fail) {
                                    throw new IllegalStateException("test failure");
                                }

                                return a + b;
                            }
                        );
                        CompletableFuture.runAsync(
                            () -> {
                                MDC.clear();

                                if (previous) {
                                    MDC.put("worker", "previous");
                                }

                                var worker = MDC.getCopyOfContextMap();

                                if (fail) {
                                    assertThrows(IllegalStateException.class, () -> callback.apply(2, 3));
                                } else {
                                    assertEquals(5, callback.apply(2, 3));
                                }

                                assertEquals(worker, MDC.getCopyOfContextMap());
                                MDC.clear();
                            }
                        ).join();
                    }
                }
            }
        } finally {
            MDC.clear();

            if (original != null) {
                MDC.setContextMap(original);
            }
        }
    }
}

package taan.online.server;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

final class RequestTask implements Runnable {
    private final AtomicReference<RequestTaskState> state = new AtomicReference<>(RequestTaskState.QUEUED);
    private final UUID operationId;
    private final Runnable operation;
    private final ThreadPoolExecutor executor;

    RequestTask(UUID operationId, Runnable operation, ThreadPoolExecutor executor) {
        this.operationId = operationId;
        this.operation = operation;
        this.executor = executor;
    }

    UUID operationId() {
        return operationId;
    }

    void remove() {
        executor.remove(this);
    }

    boolean cancel() {
        return state.compareAndSet(RequestTaskState.QUEUED, RequestTaskState.CANCELLED);
    }

    @Override
    public void run() {
        if (state.compareAndSet(RequestTaskState.QUEUED, RequestTaskState.RUNNING)) {
            try {
                operation.run();
            } finally {
                state.set(RequestTaskState.FINISHED);
            }
        }
    }
}

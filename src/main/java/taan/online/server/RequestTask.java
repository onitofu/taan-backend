package taan.online.server;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class RequestTask implements Runnable {
    private final AtomicReference<RequestTaskState> state = new AtomicReference<>(RequestTaskState.QUEUED);
    private final UUID operationId;
    private final Runnable operation;

    RequestTask(UUID operationId, Runnable operation) {
        this.operationId = operationId;
        this.operation = operation;
    }

    UUID operationId() {
        return operationId;
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

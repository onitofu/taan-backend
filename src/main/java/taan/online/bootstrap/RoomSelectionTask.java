package taan.online.bootstrap;

import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

final class RoomSelectionTask extends FutureTask<Void> {
    private final UUID selectionId;
    private final Consumer<RoomSelectionTask> completed;
    private volatile ScheduledFuture<?> timer;

    RoomSelectionTask(UUID selectionId, Runnable command, Consumer<RoomSelectionTask> completed) {
        super(command, null);
        this.selectionId = selectionId;
        this.completed = completed;
    }

    UUID selectionId() {
        return selectionId;
    }

    void timer(ScheduledFuture<?> scheduled) {
        timer = scheduled;

        if (isDone()) {
            scheduled.cancel(false);
        }
    }

    @Override
    protected void done() {
        var scheduled = timer;

        if (scheduled != null) {
            scheduled.cancel(false);
        }

        completed.accept(this);
    }
}

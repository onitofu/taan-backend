package taan.online.bootstrap;

import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

final class ScheduledMatchTask extends FutureTask<Void> {
    private final UUID matchId;
    private final Consumer<ScheduledMatchTask> completed;
    private volatile ScheduledFuture<?> timer;

    ScheduledMatchTask(UUID matchId, Runnable command, Consumer<ScheduledMatchTask> completed) {
        super(command, null);
        this.matchId = matchId;
        this.completed = completed;
    }

    UUID matchId() {
        return matchId;
    }

    void timer(ScheduledFuture<?> value) {
        timer = value;

        if (isDone()) {
            value.cancel(false);
        }
    }

    @Override
    protected void done() {
        var value = timer;

        if (value != null) {
            value.cancel(false);
        }

        completed.accept(this);
    }
}

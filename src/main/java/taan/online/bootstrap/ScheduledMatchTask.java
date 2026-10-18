package taan.online.bootstrap;

import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

final class ScheduledMatchTask extends FutureTask<Void> {
    private final UUID matchId;
    private final Runnable rejected;
    private final Consumer<ScheduledMatchTask> completed;
    private volatile ScheduledFuture<?> timer;

    ScheduledMatchTask(UUID matchId, Runnable command, Runnable rejected, Consumer<ScheduledMatchTask> completed) {
        super(command, null);
        this.matchId = matchId;
        this.rejected = rejected;
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

    void reject() {
        cancel(false);
        rejected.run();
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

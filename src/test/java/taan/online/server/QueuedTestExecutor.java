package taan.online.server;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class QueuedTestExecutor extends ThreadPoolExecutor {
    private boolean rejectWork;

    QueuedTestExecutor(BlockingQueue<Runnable> jobs) {
        super(1, 1, 0, TimeUnit.SECONDS, jobs);
    }

    @Override
    public void execute(Runnable job) {
        if (rejectWork) {
            throw new RejectedExecutionException("Queue full");
        }

        getQueue().add(job);
    }

    void rejectWork(boolean rejectWork) {
        this.rejectWork = rejectWork;
    }
}

package taan.online;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.room.service.RoomSelectionScheduler;
import taan.online.room.service.model.RoomSelectionToken;

public final class TestRoomSelectionScheduler implements RoomSelectionScheduler {
    public final List<TestRoomSelectionDeadline> tasks = new ArrayList<>();
    public long now;
    public int calls;
    public boolean failClock;
    public int failAt;

    @Override
    public void schedule(RoomSelectionToken token, long delayNanos, Runnable command) {
        if (++calls == failAt) {
            throw new IllegalStateException("Private scheduler value");
        }

        tasks.add(new TestRoomSelectionDeadline(token, now + delayNanos, command));
    }

    @Override
    public void cancel(UUID selectionId) {
        tasks.removeIf(task -> task.token().selectionId().equals(selectionId));
    }

    public long nanoTime() {
        if (failClock) {
            throw new IllegalStateException("Private clock value");
        }

        return now;
    }

    public void advanceSeconds(long seconds) {
        now += TimeUnit.SECONDS.toNanos(seconds);
        var due = tasks.stream().filter(task -> now >= task.due())
            .sorted(Comparator.comparingLong(TestRoomSelectionDeadline::due)).toList();

        for (var task : due) {
            if (tasks.remove(task)) {
                task.command().run();
            }
        }
    }
}

package taan.online;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import taan.online.match.service.MatchScheduler;
import taan.online.match.service.model.MatchDeadline;
import taan.online.room.service.model.RoomMatchProgressToken;

public final class TestMatchScheduler implements MatchScheduler {
    public final List<MatchDeadline> deadlines = new ArrayList<>();
    public final List<Runnable> commands = new ArrayList<>();
    public final List<Runnable> rejected = new ArrayList<>();
    public final List<Runnable> completions = new ArrayList<>();
    public long now;
    public boolean delayCompletions;
    public boolean rejectSchedule;
    public boolean rejectExecute;
    public Runnable onSchedule = () -> { };

    @Override
    public void schedule(MatchDeadline deadline, Runnable command, Runnable onRejected) {
        if (rejectSchedule) {
            throw new RejectedExecutionException("test scheduler unavailable");
        }

        onSchedule.run();
        deadlines.add(deadline);
        commands.add(command);
        rejected.add(onRejected);
    }

    @Override
    public void execute(RoomMatchProgressToken token, Runnable command, Runnable onRejected) {
        if (rejectExecute) {
            onRejected.run();
        } else if (delayCompletions) {
            completions.add(command);
        } else {
            command.run();
        }
    }

    @Override
    public void cancel(UUID matchId) {
        for (int index = deadlines.size() - 1; index >= 0; index--) {
            if (deadlines.get(index).token().match().matchId().equals(matchId)) {
                deadlines.remove(index);
                commands.remove(index);
                rejected.remove(index);
            }
        }
    }

    public void advanceSeconds(long seconds) {
        now += seconds * 1_000_000_000L;
        var due = new ArrayList<Runnable>();

        for (int index = deadlines.size() - 1; index >= 0; index--) {
            if (now - deadlines.get(index).deadlineNanos() >= 0) {
                due.add(commands.remove(index));
                deadlines.remove(index);
                rejected.remove(index);
            }
        }

        due.forEach(Runnable::run);
    }

    public void complete() {
        var tasks = List.copyOf(completions);
        completions.clear();
        tasks.forEach(Runnable::run);
    }
}

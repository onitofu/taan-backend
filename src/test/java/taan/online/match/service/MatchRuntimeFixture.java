package taan.online.match.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import org.slf4j.LoggerFactory;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.TestMatchScheduler;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.Match;
import taan.online.match.model.MatchId;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchToken;

final class MatchRuntimeFixture implements AutoCloseable {
    final MatchRoomFixture room = new MatchRoomFixture(2, RoomMode.ITEM);
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    final TestMatchScheduler timers = new TestMatchScheduler();
    final TestMatchOutput owner = new TestMatchOutput();
    final TestMatchOutput guest = new TestMatchOutput();
    final MatchRuntime runtime;
    final TestMatchCallbacks callbacks;
    final RoomMatchToken token;
    boolean admitted = true;
    Runnable onAccess = () -> { };

    MatchRuntimeFixture(int capacity) {
        this(capacity, MatchExecutionMode.PREVIEW);
    }

    MatchRuntimeFixture(int capacity, MatchExecutionMode mode) {
        var definitions = MatchFixtures.definitions();
        var draft = room.draft();
        token = draft.token();
        var match = new Match(
            new MatchId(token.matchId()),
            token.roomId(),
            token.generation(),
            draft.channelId(),
            draft.ownerSessionId(),
            draft.settings().mode(),
            draft.settings().rule(),
            definitions,
            new MatchPreparation(MatchFixtures.catalog()).participants(draft),
            draft.captains(),
            0
        );
        runtime = new MatchRuntime(
            match,
            new MatchBootstrapSettings(30, 20, 8, mode),
            timers,
            () -> timers.now,
            new WarningLogLimiter(
                LoggerFactory.getLogger(MatchRuntime.class),
                10,
                Duration.ofMinutes(1),
                () -> timers.now
            ),
            tasks::addLast,
            capacity,
            (context, participant) -> {
                onAccess.run();
                return admitted;
            }
        );
        callbacks = new TestMatchCallbacks(runtime);
    }

    void activate() {
        runtime.activate(
            callbacks,
            List.of(new MatchSubscription(room.owner, owner), new MatchSubscription(room.players.get(1), guest))
        );
    }

    MatchCommandContext context() {
        var snapshot = runtime.snapshot();
        return new MatchCommandContext(
            snapshot.id(),
            snapshot.roomId(),
            snapshot.roomGeneration(),
            snapshot.channelId(),
            room.owner,
            snapshot.participants().getFirst().admissionId()
        );
    }

    void run() {
        while (!tasks.isEmpty()) {
            tasks.removeFirst().run();
        }
    }

    @Override
    public void close() {
        runtime.stop();
        runtime.finish(() -> { });
        run();
        room.close();
    }
}

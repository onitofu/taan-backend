package taan.online.match.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.TestMatchScheduler;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.Match;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchId;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchToken;

final class MatchRuntimeFixture implements AutoCloseable {
    final MatchRoomFixture room;
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    final TestMatchScheduler timers = new TestMatchScheduler();
    final TestMatchOutput owner = new TestMatchOutput();
    final TestMatchOutput guest = new TestMatchOutput();
    final MatchRuntime runtime;
    final Match match;
    final TestMatchCallbacks callbacks;
    final RoomMatchToken token;
    boolean admitted = true;
    Runnable onAccess = () -> { };
    Runnable onClock = () -> { };

    MatchRuntimeFixture(int capacity) {
        this(capacity, MatchExecutionMode.PREVIEW);
    }

    MatchRuntimeFixture(int capacity, MatchExecutionMode mode) {
        this(capacity, mode, MatchLootPolicy.disabled());
    }

    MatchRuntimeFixture(int capacity, MatchExecutionMode mode, MatchLootPolicy policy) {
        this(capacity, mode, policy, RoomMode.ITEM);
    }

    MatchRuntimeFixture(int capacity, MatchExecutionMode mode, MatchLootPolicy policy, RoomMode roomMode) {
        this(capacity, mode, policy, roomMode, MatchBoxPolicy.DISABLED);
    }

    MatchRuntimeFixture(
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy,
        RoomMode roomMode,
        MatchBoxPolicy boxes
    ) {
        room = new MatchRoomFixture(2, roomMode);
        var definitions = MatchFixtures.definitions();
        var draft = room.draft();
        token = draft.token();
        match = new Match(
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
            0,
            boxes
        );
        runtime = new MatchRuntime(
            match,
            new MatchBootstrapSettings(
                30,
                20,
                8,
                mode,
                TimeUnit.SECONDS.toNanos(20),
                TimeUnit.SECONDS.toNanos(10),
                policy,
                boxes
            ),
            timers,
            () -> {
                onClock.run();
                return timers.now;
            },
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

package taan.online.match.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
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
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
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
    boolean rejectWork;
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
        this(capacity, mode, policy, roomMode, boxes, roomMode.usesItems() ? RoomRule.BONUS : RoomRule.BASIC);
    }

    MatchRuntimeFixture(
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy,
        RoomMode roomMode,
        MatchBoxPolicy boxes,
        RoomRule rule
    ) {
        this(capacity, mode, policy, roomMode, boxes, rule, 2);
    }

    MatchRuntimeFixture(
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy,
        RoomMode roomMode,
        MatchBoxPolicy boxes,
        RoomRule rule,
        int count
    ) {
        room = new MatchRoomFixture(count, roomMode);
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
            rule,
            definitions,
            new MatchPreparation(MatchFixtures.catalog()).participants(draft),
            roomMode.requiresCaptainSelection() ? new CaptainSelectionResult(0, 2) : draft.captains(),
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
            command -> {
                if (rejectWork) {
                    throw new RejectedExecutionException("test executor unavailable");
                }
                tasks.addLast(command);
            },
            capacity,
            (context, participant) -> {
                onAccess.run();
                return admitted;
            }
        );
        callbacks = new TestMatchCallbacks(runtime);
    }

    void activate() {
        var subscriptions = new ArrayList<MatchSubscription>();

        for (int index = 0; index < room.players.size(); index++) {
            var output = index == 0 ? owner : index == 1 ? guest : new TestMatchOutput();
            subscriptions.add(new MatchSubscription(room.players.get(index), output));
        }

        runtime.activate(callbacks, subscriptions);
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

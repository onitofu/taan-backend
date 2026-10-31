package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.TestAwait;
import taan.online.match.model.MatchId;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;

class MatchRegistryConcurrencyTest {
    @Test
    void cancelsOneRuntimeExactlyOnceWhenBothCallersAlreadyResolvedItsIdentity() throws Exception {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var registry = MatchFixtures.registry();
            var token = f.draft();
            registry.reserve(token);
            var runtime = runtime(registry, token.token());
            var field = MatchRuntime.class.getDeclaredField("admission");
            field.setAccessible(true);
            var lock = field.get(runtime);
            var first = new CompletableFuture<RoomMatchReservationResult>();
            var second = new CompletableFuture<RoomMatchReservationResult>();
            Thread firstThread;
            Thread secondThread;

            synchronized (lock) {
                firstThread = Thread.ofPlatform().start(() -> first.complete(registry.cancel(token.token(), "FIRST")));
                blocked(firstThread);
                secondThread = Thread.ofPlatform().start(
                    () -> second.complete(registry.cancel(token.token(), "SECOND"))
                );
                blocked(secondThread);
            }

            firstThread.join(5000);
            secondThread.join(5000);

            assertEquals(
                Set.of(RoomMatchReservationOutcome.ROLLED_BACK, RoomMatchReservationOutcome.STALE),
                Set.of(first.join().outcome(), second.join().outcome())
            );
            assertTrue(registry.current(token.token()).isEmpty());
        }
    }

    private MatchRuntime runtime(MatchRegistry registry, RoomMatchToken token) throws Exception {
        var field = MatchRegistry.class.getDeclaredField("matches");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var matches = (Map<MatchId, MatchRuntime>) field.get(registry);
        return matches.get(new MatchId(token.matchId()));
    }

    private void blocked(Thread thread) {
        TestAwait.until("contending admission lock", () -> thread.getState() == Thread.State.BLOCKED);
    }
}

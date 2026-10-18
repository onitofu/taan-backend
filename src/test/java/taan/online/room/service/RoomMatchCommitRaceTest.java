package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.TestMatchScheduler;
import taan.online.TestRoomSelectionScheduler;
import taan.online.match.service.TestMatchOutput;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.session.model.SessionId;

class RoomMatchCommitRaceTest {
    @Test
    void revalidatesTheFrozenRosterIfAuthenticationExpiresDuringReservation() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var invalidated = new AtomicBoolean();
            RandomGenerator random = () -> {
                if (invalidated.compareAndSet(false, true)) {
                    scenario.sessions.close(new SessionId(guest));
                }

                return 0;
            };
            var matches = MatchFixtures.registry(MatchFixtures.definitions(), new TestMatchScheduler(), random);
            var a = new TestMatchOutput();
            var b = new TestMatchOutput();
            matches.subscribe(owner, a);
            matches.subscribe(guest, b);

            try (var rooms = new RoomRegistry(
                scenario.channels,
                RoomScenario.maps(),
                new TestRoomSelectionScheduler(),
                matches
            )) {
                rooms.create(
                    scenario.channels.membership(owner).orElseThrow(),
                    RoomScenario.settings(),
                    null,
                    scenario.owner(owner),
                    new RecordingRoomOutput()
                );
                var context = scenario.channels.membership(guest).orElseThrow();
                rooms.enter(
                    context,
                    rooms.entryTarget(context, 1).orElseThrow(),
                    scenario.owner(guest),
                    "",
                    new RecordingRoomOutput()
                );
                rooms.setReady(guest, true);
                var result = rooms.requestStart(owner);

                assertEquals(RoomStartOutcome.MATCH_UNAVAILABLE, result.outcome());
                assertEquals("SESSION_CLOSED", result.reason());
                assertEquals(RoomState.WAITING, rooms.current(owner).orElseThrow().state());
                assertTrue(a.starts.isEmpty());
                assertTrue(b.starts.isEmpty());
                assertTrue(a.closed.isEmpty());
                assertTrue(b.closed.isEmpty());
            }
        }
    }
}

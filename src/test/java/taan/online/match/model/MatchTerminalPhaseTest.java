package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;

class MatchTerminalPhaseTest {
    @Test
    void onlyEntersPreviewAfterLoadingAndTerminatesOnce() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var snapshot = f.scenario.matches.current(token).orElseThrow();
            var match = new Match(
                snapshot.id(),
                snapshot.roomId(),
                snapshot.roomGeneration(),
                snapshot.channelId(),
                snapshot.ownerSessionId(),
                snapshot.mode(),
                snapshot.rule(),
                MatchFixtures.definitions(),
                snapshot.participants(),
                null,
                snapshot.firstTurnSlot()
            );

            assertFalse(match.previewStarted(0, 20_000_000_000L));
            assertTrue(match.commit(0, 30_000_000_000L));
            assertFalse(match.beginSynchronization());

            for (var member : snapshot.participants()) {
                match.deliveryCompleted(member.sessionId());
                match.loadingDone(
                    member.sessionId(),
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                );
            }

            assertTrue(match.beginSynchronization());
            snapshot.participants().forEach(member -> match.deliveryCompleted(member.sessionId()));
            assertTrue(match.previewStarted(0, 20_000_000_000L));
            assertFalse(match.previewStarted(0, 20_000_000_000L));
            assertTrue(match.beginTermination());
            assertFalse(match.beginTermination());
            assertTrue(match.cancel());
            assertFalse(match.beginTermination());
        }
    }
}

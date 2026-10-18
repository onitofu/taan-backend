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
                null
            );

            assertFalse(match.previewStarted());
            assertTrue(match.commit());
            assertTrue(match.previewStarted());
            assertFalse(match.previewStarted());
            assertTrue(match.beginTermination());
            assertFalse(match.beginTermination());
            assertTrue(match.cancel());
            assertFalse(match.beginTermination());
        }
    }
}

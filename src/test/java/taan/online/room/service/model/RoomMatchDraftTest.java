package taan.online.room.service.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;

class RoomMatchDraftTest {
    @Test
    void rejectsInvalidChannelLootScalarsBeforeReservingAMatch() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var draft = f.draft();

            for (int scalar : new int[]{-1, 101}) {
                assertThrows(IllegalArgumentException.class, () -> new RoomMatchDraft(
                    draft.token(),
                    draft.channelId(),
                    draft.ownerSessionId(),
                    draft.settings(),
                    draft.participants(),
                    draft.captains(),
                    scalar
                ));
            }
        }
    }
}

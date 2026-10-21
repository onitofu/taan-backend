package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;

class RoomProgressTest {
    @Test
    void ignoresProgressForAnotherSessionOrCharacterAndAppliesAbsoluteSnapshotsIdempotently() {
        var session = UUID.randomUUID();
        var character = CharacterFixtures.character(1, new AccountId(1), AccountGender.MALE, "HeroOne");
        var room = new Room(
            new RoomId(1),
            UUID.randomUUID(),
            1,
            RoomScenario.settings(),
            null,
            new RoomParticipant(session, character, 0)
        );
        var progress = new CharacterProgressSnapshot(character.id(), 2, new CharacterProgress(940, 1, 1, 0));
        room.refreshProgress(UUID.randomUUID(), progress);
        room.refreshProgress(session, new CharacterProgressSnapshot(new CharacterId(2), 2, progress.progress()));

        assertEquals(character, room.slots().getFirst().participant().character());

        room.refreshProgress(session, progress, 123L);
        room.refreshProgress(session, progress);
        room.updateAppearance(session, CharacterFixtures.appearance(AccountGender.FEMALE));

        assertEquals(123, room.slots().getFirst().participant().character().gold());
        assertEquals(2, room.slots().getFirst().participant().character().level());
        assertEquals(progress.progress(), room.slots().getFirst().participant().character().progress());
        room.changeTeam(session, RoomTeam.RED);
        assertEquals(2, room.slots().get(2).participant().character().level());
        assertEquals("MISSING_TEAM", room.startRejection(session));
    }
}

package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.RoomScenario;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.room.view.RoomSnapshotMapper;

class RoomOutputValidatorTest {
    @Test
    void boundsTheLargestEightPlayerInitializationAndItsFramePrefix() {
        var settings = RoomScenario.settings();
        var owner = participant(1);
        var room = new Room(
            new RoomId(1),
            UUID.randomUUID(),
            1,
            new RoomSettings(new RoomTitle("x".repeat(256)), settings.mode(), settings.rule(), 1, 8),
            null,
            owner
        );

        for (int i = 2; i <= 8; i++) {
            room.enter(participant(i), "");
        }

        var snapshot = new RoomSnapshot(
            room.id(),
            room.generation(),
            1,
            room.settings(),
            owner.sessionId(),
            room.slots(),
            false,
            room.state()
        );
        int bytes = new RoomResponseCodec().encode(new RoomSnapshotMapper().map(snapshot)).length;
        var env = TestFixtures.environment();
        var validator = new RoomOutputValidator();
        env.put("BACKEND_MAX_FRAME_BYTES", Integer.toString(bytes));
        env.put("BACKEND_MAX_PENDING_OUTPUT_BYTES", Integer.toString(bytes + 4));

        assertEquals(2458, bytes);
        validator.validate(ApplicationConfig.fromEnvironment(env));
        env.put("BACKEND_MAX_FRAME_BYTES", Integer.toString(bytes - 1));
        assertThrows(
            InvalidConfigurationException.class,
            () -> validator.validate(ApplicationConfig.fromEnvironment(env))
        );
        env.put("BACKEND_MAX_FRAME_BYTES", Integer.toString(bytes));
        env.put("BACKEND_MAX_PENDING_OUTPUT_BYTES", Integer.toString(bytes + 3));
        assertThrows(
            InvalidConfigurationException.class,
            () -> validator.validate(ApplicationConfig.fromEnvironment(env))
        );
    }

    private RoomParticipant participant(long id) {
        return new RoomParticipant(
            UUID.randomUUID(),
            CharacterFixtures.character(id, new AccountId(id), AccountGender.MALE, "x".repeat(18)),
            3
        );
    }
}

package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.RecordingRoomOutput;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.SelectionFixtures;
import taan.online.TestRoomSelectionScheduler;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.service.ChannelTestAccess;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.CreateRoomOutcome;

class RoomRegistryStaleSessionTest {
    @Test
    void releasesAnInvalidatedOldSessionBeforePublishingAReconnectedCharacter() {
        var access = new ChannelTestAccess();
        var first = access.add(1, 1);
        var channels = SelectionFixtures.registry(access);
        channels.move(first, new MoveToCommand(0, 1, 0, 929));
        channels.enter(first, RoomListingFixtures.emptyView(new CollectingPacketOutput()));
        var actor = access.players.get(first);
        var character = CharacterFixtures.character(1, new AccountId(1), AccountGender.MALE, "Hero1");

        try (var rooms = new RoomRegistry(channels, RoomScenario.maps(), new TestRoomSelectionScheduler())) {
            var old = rooms.create(
                channels.membership(first).orElseThrow(),
                RoomScenario.settings(),
                null,
                new RoomParticipant(first, character, 3),
                new RecordingRoomOutput()
            );
            var token = rooms.membership(first).orElseThrow();
            var next = UUID.randomUUID();
            access.players.put(
                next,
                new ChannelParticipant(
                    next,
                    actor.accountId(),
                    actor.characterId(),
                    actor.name(),
                    actor.level(),
                    actor.gender()
                )
            );
            access.players.remove(first);
            channels.move(next, new MoveToCommand(0, 2, 0, 929));
            channels.enter(next, RoomListingFixtures.emptyView(new CollectingPacketOutput()));

            var current = rooms.create(
                channels.membership(next).orElseThrow(),
                RoomScenario.settings(),
                null,
                new RoomParticipant(next, character, 3),
                new RecordingRoomOutput()
            );

            assertEquals(CreateRoomOutcome.CREATED, current.outcome());
            assertTrue(rooms.current(first).isEmpty());
            assertEquals(old.room().id(), current.room().id());
            assertNotEquals(old.room().generation(), current.room().generation());
            rooms.release(token, "LATE_OLD_CLOSE");
            rooms.release(first, "LATE_OLD_CLOSE");

            assertEquals(current.room(), rooms.current(next).orElseThrow());
        }
    }
}

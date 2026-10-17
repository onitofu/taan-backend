package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.SelectionFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.service.ChannelTestAccess;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomTeam;
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

        try (var rooms = new RoomRegistry(channels)) {
            var old = rooms.create(
                channels.membership(first).orElseThrow(),
                RoomScenario.settings(),
                new RoomParticipant(first, character, 0, RoomTeam.BLUE, 3)
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
                new RoomParticipant(next, character, 0, RoomTeam.BLUE, 3)
            );

            assertEquals(CreateRoomOutcome.CREATED, current.outcome());
            assertTrue(rooms.current(first).isEmpty());
            assertNotEquals(old.room().id(), current.room().id());
            rooms.release(token, "LATE_OLD_CLOSE");
            rooms.release(first, "LATE_OLD_CLOSE");

            assertEquals(current.room(), rooms.current(next).orElseThrow());
        }
    }
}

package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
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
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomModelFixtures;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.RoomSnapshot;

class QuickJoinRegistryTest {
    @Test
    void skipsProtectedForeignFullAndClosedSlotRoomsAndAdmitsToEitherSupportedMode() throws Exception {
        try (var scenario = new RoomScenario()) {
            var snapshots = new ArrayList<RoomSnapshot>();

            for (int i = 1; i <= 6; i++) {
                var owner = scenario.player(i, i == 2 ? 2 : 1);
                var command = RoomScenario.command(
                    5,
                    0,
                    "Room",
                    0,
                    i == 6 ? 1 : 0,
                    i == 6 ? 0 : 2,
                    0,
                    8,
                    i == 1 || i == 3 ? "secret" : null,
                    0
                );
                snapshots.add(scenario.create.execute(owner, command, new RecordingRoomOutput()).room());
            }

            var rooms = mutableRooms(scenario.rooms);
            var closed = snapshots.get(3);
            rooms.put(
                closed.id(),
                RoomModelFixtures.withClosedPlaces(
                    closed.id(),
                    closed.generation(),
                    1,
                    closed.settings(),
                    closed.owner()
                )
            );
            int full = snapshots.get(4).id().value();

            for (int i = 100; i < 107; i++) {
                var member = scenario.player(i, 1);
                scenario.enter.execute(member, full, "", new RecordingRoomOutput());
            }

            var joiner = scenario.player(7, 1);
            var output = new RecordingRoomOutput();
            var result = scenario.quickJoin.execute(joiner, 65535, output);

            assertEquals(EnterRoomOutcome.ENTERED, result.outcome());
            assertEquals(snapshots.get(5).id(), output.initializations.getFirst().id());
            scenario.rooms.release(snapshots.get(5).ownerSessionId(), "LEFT");
            scenario.rooms.release(joiner, "LEFT");
            scenario.lobby(joiner);

            assertEquals(
                "NO_MATCHING_ROOM",
                scenario.quickJoin.execute(joiner, 65535, new RecordingRoomOutput()).reason()
            );
            assertTrue(scenario.rooms.membership(joiner).isEmpty());
            assertEquals(1, scenario.rooms.forChannel(2).getFirst().playerCount());
            assertEquals(8, rooms.get(snapshots.get(4).id()).playerCount());
            assertEquals(1, rooms.get(snapshots.getFirst().id()).playerCount());
        }
    }

    @Test
    void skipsANonWaitingRoomEvenIfItRemainsInTheLookupIndex() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var created = scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput()).room();
            mutableRooms(scenario.rooms).get(created.id()).close();
            var output = new RecordingRoomOutput();
            var result = scenario.quickJoin.execute(guest, 65535, output);

            assertEquals(EnterRoomOutcome.NOT_FOUND, result.outcome());
            assertTrue(output.initializations.isEmpty());
            assertTrue(scenario.rooms.membership(guest).isEmpty());
        }
    }

    @Test
    void checksMembershipAndTheAuthoritativeCharacterBindingAtTheRegistryBoundary() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            var context = scenario.channels.membership(guest).orElseThrow();
            var output = new RecordingRoomOutput();
            var forged = new RoomParticipant(guest, scenario.owner(owner).character(), 3);
            var changed = new RoomParticipant(
                guest,
                CharacterFixtures.character(99, new AccountId(2), AccountGender.FEMALE, "OtherHero"),
                3
            );

            assertEquals(EnterRoomOutcome.REJECTED, scenario.rooms.quickJoin(context, forged, output).outcome());
            assertEquals(EnterRoomOutcome.REJECTED, scenario.rooms.quickJoin(context, changed, output).outcome());
            var ownerContext = scenario.channels.membership(owner).orElseThrow();

            assertEquals(
                EnterRoomOutcome.ALREADY_IN_ROOM,
                scenario.rooms.quickJoin(ownerContext, scenario.owner(owner), output).outcome()
            );
            assertTrue(output.initializations.isEmpty());
        }
    }

    @Test
    void excludesDuplicatePersistentIdentitiesEvenWhenAnAdapterPresentsAnotherSession() {
        for (boolean duplicateCharacter : new boolean[]{true, false}) {
            var access = new ChannelTestAccess();
            var a = access.add(1, 1);
            var b = access.add(2, 1);
            var actor = access.players.get(b);
            var character = CharacterFixtures.character(
                duplicateCharacter ? 1 : 2,
                new AccountId(duplicateCharacter ? 2 : 1),
                AccountGender.MALE,
                "Guest"
            );
            access.players.put(
                b,
                new ChannelParticipant(
                    b,
                    character.accountId(),
                    character.id(),
                    actor.name(),
                    actor.level(),
                    actor.gender()
                )
            );
            var channels = SelectionFixtures.registry(access);
            channels.move(a, new MoveToCommand(0, 1, 0, 929));
            channels.enter(a, RoomListingFixtures.emptyView(new CollectingPacketOutput()));

            try (var rooms = new RoomRegistry(channels, RoomScenario.maps(), new TestRoomSelectionScheduler())) {
                var owner = CharacterFixtures.character(1, new AccountId(1), AccountGender.MALE, "Owner");
                rooms.create(
                    channels.membership(a).orElseThrow(),
                    RoomScenario.settings(),
                    null,
                    new RoomParticipant(a, owner, 3),
                    new RecordingRoomOutput()
                );

                if (duplicateCharacter) {
                    access.players.remove(a);
                }

                channels.move(b, new MoveToCommand(0, 1, 0, 929));
                channels.enter(b, RoomListingFixtures.emptyView(new CollectingPacketOutput()));
                var output = new RecordingRoomOutput();
                var result = rooms.quickJoin(
                    channels.membership(b).orElseThrow(),
                    new RoomParticipant(b, character, 3),
                    output
                );

                assertEquals(EnterRoomOutcome.NOT_FOUND, result.outcome());
                assertTrue(output.initializations.isEmpty());
                assertTrue(rooms.membership(b).isEmpty());
            }
        }
    }

    private Map<RoomId, Room> mutableRooms(RoomRegistry registry) throws Exception {
        var field = RoomRegistry.class.getDeclaredField("rooms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rooms = (Map<RoomId, Room>) field.get(registry);
        return rooms;
    }
}

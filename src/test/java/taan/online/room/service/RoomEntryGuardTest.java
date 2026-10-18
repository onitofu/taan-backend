package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomEntryFixtures;
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
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomModelFixtures;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomPassword;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.RoomEntryTarget;
import taan.online.session.model.SessionId;

class RoomEntryGuardTest {
    @Test
    void rechecksActorTargetGenerationChannelAndConcurrentMembership() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var c = scenario.player(3, 2);
            int room = scenario.create.execute(
                a,
                RoomScenario.command(),
                new RecordingRoomOutput()
            ).room().id().value();
            var context = scenario.channels.membership(b).orElseThrow();
            var target = scenario.rooms.entryTarget(context, room).orElseThrow();
            var out = new RecordingRoomOutput();
            var owner = scenario.owner(b);

            assertEquals(
                EnterRoomOutcome.REJECTED,
                scenario.rooms.enter(
                    context,
                    target,
                    new RoomParticipant(b, scenario.owner(a).character(), 3),
                    "",
                    out
                ).outcome()
            );
            var changed = CharacterFixtures.character(
                99,
                owner.character().accountId(),
                AccountGender.FEMALE,
                "Changed"
            );
            assertEquals(
                EnterRoomOutcome.REJECTED,
                scenario.rooms.enter(context, target, new RoomParticipant(b, changed, 3), "", out).outcome()
            );
            assertEquals(
                EnterRoomOutcome.NOT_FOUND,
                scenario.rooms.enter(
                    context,
                    new RoomEntryTarget(target.id(), UUID.randomUUID(), 1),
                    owner,
                    "",
                    out
                ).outcome()
            );
            assertEquals(
                EnterRoomOutcome.NOT_FOUND,
                scenario.rooms.enter(
                    context,
                    new RoomEntryTarget(target.id(), target.generation(), 2),
                    owner,
                    "",
                    out
                ).outcome()
            );
            assertEquals(
                EnterRoomOutcome.NOT_FOUND,
                scenario.rooms.enter(
                    scenario.channels.membership(c).orElseThrow(),
                    target,
                    scenario.owner(c),
                    "",
                    out
                ).outcome()
            );
            scenario.characters.onCommit = () -> scenario.rooms.enter(context, target, owner, "", out);

            assertEquals(EnterRoomOutcome.ALREADY_IN_ROOM, scenario.enter.execute(b, room, "", out).outcome());
            assertEquals(1, out.initializations.size());
            assertEquals(
                EnterRoomOutcome.ALREADY_IN_ROOM,
                scenario.rooms.enter(context, target, owner, "", out).outcome()
            );
        }
    }

    @Test
    void returnsSlotFailureForAValidClosedLayoutAndMapsItToKnownWireReasonEight() throws Exception {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var snapshot = scenario.create.execute(a, RoomScenario.command(), new RecordingRoomOutput()).room();
            var field = RoomRegistry.class.getDeclaredField("rooms");
            field.setAccessible(true);
            Object value = field.get(scenario.rooms);
            @SuppressWarnings("unchecked")
            var rooms = (Map<RoomId, Room>) value;
            rooms.put(
                snapshot.id(),
                RoomModelFixtures.withClosedPlaces(
                    snapshot.id(),
                    snapshot.generation(),
                    snapshot.channelId(),
                    snapshot.settings(),
                    snapshot.owner()
                )
            );
            var out = new CollectingPacketOutput();
            scenario.handler.handle(b, PqGameSessionEnter.TYPE_ID, RoomEntryFixtures.enter(snapshot.id().value()), out);

            assertEquals(8, new RoomResponseCodec().enterFail(out.batches.getFirst().getFirst()).reason());
            assertTrue(scenario.rooms.membership(b).isEmpty());
            assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void reportsOnlyTheFailedRecipientAndLetsOthersReceiveTheNewRoster() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var c = scenario.player(3, 1);
            var bad = new RecordingRoomOutput();
            var good = new RecordingRoomOutput();
            int room = scenario.create.execute(a, RoomScenario.command(), bad).room().id().value();
            scenario.enter.execute(b, room, "", good);
            bad.onUpdate = () -> {
                throw new IllegalStateException("Private room password");
            };

            assertEquals(
                EnterRoomOutcome.ENTERED,
                scenario.enter.execute(c, room, "", new RecordingRoomOutput()).outcome()
            );
            assertEquals("ROOM_NOTIFICATION_FAILED", bad.closed);
            assertEquals(1, good.updates.size());
            assertFalse(logs.text().contains("Private room password"));
            assertTrue(logs.text().contains("room.notification.failed"));
            bad.onUpdate = () -> { };
            scenario.rooms.release(a, "WRITE_FAILED");

            assertEquals(c, scenario.rooms.current(c).orElseThrow().ownerSessionId());
            var newcomer = scenario.player(4, 1);
            var initialFailure = new RecordingRoomOutput();
            initialFailure.onInitialize = () -> {
                throw new IllegalStateException("Private credential");
            };
            var result = scenario.enter.execute(newcomer, room, "", initialFailure);

            assertTrue(result.delivery().toCompletableFuture().isCompletedExceptionally());
            assertEquals("ROOM_NOTIFICATION_FAILED", initialFailure.closed);
            assertFalse(logs.text().contains("Private credential"));
        }
    }

    @Test
    void rejectsDuplicatePersistentIdentitiesEvenWhenTheSessionAccessReturnsAnotherBinding() {
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
                int id = rooms.create(
                    channels.membership(a).orElseThrow(),
                    RoomScenario.settings(),
                    null,
                    new RoomParticipant(a, owner, 3),
                    new RecordingRoomOutput()
                ).room().id().value();
                if (duplicateCharacter) {
                    access.players.remove(a);
                }

                channels.move(b, new MoveToCommand(0, 1, 0, 929));
                channels.enter(b, RoomListingFixtures.emptyView(new CollectingPacketOutput()));
                var context = channels.membership(b).orElseThrow();
                var target = rooms.entryTarget(context, id).orElseThrow();

                assertEquals(
                    EnterRoomOutcome.ALREADY_IN_ROOM,
                    rooms.enter(
                        context,
                        target,
                        new RoomParticipant(b, character, 3),
                        "",
                        new RecordingRoomOutput()
                    ).outcome()
                );
                assertTrue(rooms.membership(b).isEmpty());
            }
        }
    }

    @Test
    void mapsAlreadyJoinedAndGenericFailuresAndSuppressesAnEntryCompletedAfterDisconnect() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 0);
            int room = scenario.create.execute(
                a,
                RoomScenario.command(),
                new RecordingRoomOutput()
            ).room().id().value();
            var output = new CollectingPacketOutput();
            scenario.handler.handle(a, PqGameSessionEnter.TYPE_ID, RoomEntryFixtures.enter(room), output);

            assertEquals(3, new RoomResponseCodec().enterFail(output.batches.getLast().getFirst()).reason());
            scenario.handler.handle(b, PqGameSessionEnter.TYPE_ID, RoomEntryFixtures.enter(room), output);

            assertEquals(0, new RoomResponseCodec().enterFail(output.batches.getLast().getFirst()).reason());
            scenario.channels.move(b, new MoveToCommand(0, 1, 0, 929));
            scenario.lobby(b);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(b));
            int count = output.batches.size();
            scenario.handler.handle(b, PqGameSessionEnter.TYPE_ID, RoomEntryFixtures.enter(room), output);

            assertEquals(count, output.batches.size());
            assertTrue(scenario.rooms.membership(b).isEmpty());
        }
    }

    @Test
    void keepsRoomNotificationsSeparateWithinOneChannel() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var c = scenario.player(3, 1);
            int first = scenario.create.execute(
                a,
                RoomScenario.command(),
                new RecordingRoomOutput()
            ).room().id().value();
            scenario.enter.execute(b, first, "", new RecordingRoomOutput());
            var out = new RecordingRoomOutput();
            var second = scenario.create.execute(c, RoomScenario.command(), out).room();
            scenario.rooms.release(b, "PEER_CLOSED");

            assertTrue(out.updates.isEmpty());
            assertEquals(second, scenario.rooms.current(c).orElseThrow());
            assertEquals(2, scenario.rooms.forChannel(1).size());
        }
    }

    @Test
    void rejectsAnIdReusedWhileLoadingTheCharacterAndKeepsTheReplacementRoomIntact() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var pool = new RoomIdPool();
            for (int i = 2; i <= 65535; i++) {
                pool.allocate();
            }
            try (var rooms = new RoomRegistry(
                scenario.channels,
                pool,
                RoomScenario.maps(),
                new TestRoomSelectionScheduler(),
                System::nanoTime,
                new Random(1)
            )) {
                var first = rooms.create(
                    scenario.channels.membership(a).orElseThrow(),
                    RoomScenario.settings(),
                    new RoomPassword("old"),
                    scenario.owner(a),
                    new RecordingRoomOutput()
                ).room();
                var entry = new EnterRoomAction(
                    scenario.access,
                    scenario.channels,
                    rooms,
                    new RoomCharacterLoader(scenario.characters, InventoryFixtures.catalog()),
                    new RoomBootstrapDefinition(1, 3)
                );
                scenario.characters.onCommit = () -> {
                    rooms.release(a, "LEFT");
                    scenario.lobby(a);
                    rooms.create(
                        scenario.channels.membership(a).orElseThrow(),
                        RoomScenario.settings(),
                        new RoomPassword("new"),
                        scenario.owner(a),
                        new RecordingRoomOutput()
                    );
                };

                assertEquals(
                    EnterRoomOutcome.NOT_FOUND,
                    entry.execute(b, first.id().value(), "new", new RecordingRoomOutput()).outcome()
                );
                assertEquals(first.id(), rooms.current(a).orElseThrow().id());
                assertNotEquals(first.generation(), rooms.current(a).orElseThrow().generation());
                assertTrue(rooms.membership(b).isEmpty());
            }
        }
    }
}

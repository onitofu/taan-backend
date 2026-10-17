package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.leave;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionStartGame;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.session.model.SessionId;

class RoomPacketHandlerTest {
    @Test
    void mapsARealOwnerAndAppearanceToEightSlotsAndPreservesTheRoomOnDuplicateCreateAndStart() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var codec = new RoomResponseCodec();
            var reply = scenario.handler.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            var init = codec.initialize(reply.responses().getFirst());

            assertEquals(RoomReplyOutcome.CREATED, reply.outcome());
            assertEquals(1, init.info().roomId());
            assertEquals(1, init.info().state());
            assertEquals(1, init.info().details().playerCount());
            assertEquals(8, init.slots().size());
            assertEquals(1, init.option().ownerAccountId());
            assertEquals(1, init.option().ownerCharacterId());
            assertEquals("Hero1", init.slots().getFirst().occupant().name());
            assertEquals(1, init.slots().getFirst().occupant().team());
            assertEquals(3, init.slots().getFirst().occupant().weaponId());
            assertEquals(2, init.slots().getFirst().occupant().appearance().mode());
            assertTrue(init.slots().stream().skip(1).allMatch(slot -> slot.state() == 0 && slot.occupant() == null));

            var duplicate = scenario.handler.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            assertEquals(RoomReplyOutcome.CREATE_REJECTED, duplicate.outcome());
            assertEquals(0, codec.createFail(duplicate.responses().getFirst()).reason());
            var start = scenario.handler.handle(id, PqGameSessionStartGame.TYPE_ID, root("start-empty"));

            assertEquals(RoomReplyOutcome.START_UNAVAILABLE, start.outcome());
            assertEquals(
                "Starting a match is currently unavailable.",
                new NoticeMessageCodec().decode(start.responses().getFirst()).message()
            );
            assertTrue(scenario.rooms.current(id).isPresent());
            byte[] exposed = reply.responses().getFirst();
            exposed[0] = 0;

            assertEquals(init, codec.initialize(reply.responses().getFirst()));
        }
    }

    @Test
    void mapsCreateFailuresLeaveOutcomesAndClosedSessionsWithoutReinitializingState() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            for (String fixture : List.of("create-password", "info-without-details")) {
                byte[] request = fixture.equals("info-without-details") ? new BinaryWriter()
                    .unsignedInt(PqGameSessionCreate.TYPE_ID).unsignedInt(0x2e2a05ea).unsignedShort(999)
                    .unsignedByte(0).bytes(new byte[21]).toByteArray() : root(fixture);
                assertEquals(
                    RoomReplyOutcome.CREATE_REJECTED,
                    scenario.handler.handle(id, PqGameSessionCreate.TYPE_ID, request).outcome()
                );
            }

            scenario.create.execute(id, RoomScenario.command());
            var result = scenario.handler.handle(id, PqGameSessionLeave.TYPE_ID, leave(999, 0));

            assertEquals(RoomReplyOutcome.LEAVE_REJECTED, result.outcome());
            assertEquals(0, new RoomResponseCodec().leave(result.responses().getFirst()).success());
            assertEquals(
                RoomReplyOutcome.LEFT,
                scenario.handler.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome()
            );
            assertEquals(
                RoomReplyOutcome.LEAVE_REPLAYED,
                scenario.handler.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome()
            );
            scenario.handler.close(id, "TEST");
            scenario.sessions.close(new SessionId(id));

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handler.handle(id, PqGameSessionCreate.TYPE_ID, new byte[0]).outcome()
            );
            assertTrue(scenario.handler.handle(id, PqGameSessionStartGame.TYPE_ID, new byte[0]).responses().isEmpty());
        }
    }

    @Test
    void ignoresCreationThatFinishesAfterSessionClosure() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(id));

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handler.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected")).outcome()
            );
            assertTrue(scenario.rooms.current(id).isEmpty());
        }
    }

    @Test
    void mapsNoItemAndRedTeamSnapshotsWithoutWireValuesInTheDomain() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var original = scenario.create.execute(id, RoomScenario.command()).room();
            var settings = new RoomSettings(original.settings().title(), RoomMode.NO_ITEM, RoomRule.BASIC, 1, 8);
            var owner = new RoomParticipant(id, original.owner().character(), 1, RoomTeam.RED, 3);
            var snapshot = new RoomSnapshot(original.id(), original.generation(), 1, settings, owner);
            var result = new RoomSnapshotMapper().map(snapshot);

            assertEquals(1, result.info().details().gameType());
            assertEquals(0, result.info().details().rule());
            assertEquals(2, result.slots().get(1).occupant().team());
        }
    }

    @Test
    void discardsRepliesWhenTheSessionClosesBetweenTheActionAndResponseMapping() throws Exception {
        for (int type : new int[]{
            PqGameSessionCreate.TYPE_ID,
            PqGameSessionEnter.TYPE_ID,
            PqGameSessionLeave.TYPE_ID,
            PqGameSessionStartGame.TYPE_ID
        }) {
            try (var scenario = new RoomScenario()) {
                var id = scenario.player(1, 1);
                if (type != PqGameSessionCreate.TYPE_ID) {
                    scenario.create.execute(id, RoomScenario.command());
                }
                int[] calls = {0};
                SelectedCharacterAccess guarded = session -> {
                    if (++calls[0] == 2) {
                        scenario.sessions.close(new SessionId(session));
                    }
                    return scenario.access.selectedCharacter(session);
                };
                var handler = new RoomPacketHandler(
                    guarded,
                    scenario.create,
                    scenario.leave,
                    scenario.start,
                    scenario.rooms
                );
                byte[] request = type == PqGameSessionCreate.TYPE_ID ? root("create-unprotected")
                    : type == PqGameSessionEnter.TYPE_ID ? root("enter-empty-password")
                    : type == PqGameSessionLeave.TYPE_ID ? leave(1, 0) : root("start-empty");

                assertEquals(RoomReplyOutcome.IGNORED, handler.handle(id, type, request).outcome());
            }
        }
    }

    @Test
    void ignoresLeaveAndStartThatDiscoverClosureDuringExecution() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.create.execute(id, RoomScenario.command());
            scenario.channels.release(id, "CHANNEL_CLOSED");

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handler.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome()
            );
        }

        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            SelectedCharacterAccess closing = session -> {
                var actor = scenario.access.selectedCharacter(session);
                scenario.sessions.close(new SessionId(session));
                return actor;
            };
            var handler = new RoomPacketHandler(
                closing,
                scenario.create,
                scenario.leave,
                scenario.start,
                scenario.rooms
            );

            assertEquals(
                RoomReplyOutcome.IGNORED,
                handler.handle(id, PqGameSessionStartGame.TYPE_ID, root("start-empty")).outcome()
            );
        }
    }

}

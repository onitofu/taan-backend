package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.leave;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.RecordingRoomOutput;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionStartGame;
import taan.online.room.service.ChangeRoomTeamAction;
import taan.online.room.service.SetRoomReadyAction;
import taan.online.room.service.SetRoomSlotAvailabilityAction;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.room.view.RoomSnapshotMapper;
import taan.online.session.model.SessionId;

class RoomPacketHandlerTest {
    @Test
    void mapsARealOwnerAndAppearanceToEightSlotsAndPreservesTheRoomOnDuplicateCreateAndStart() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var codec = new RoomResponseCodec();
            var reply = scenario.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            var init = codec.initialize(scenario.lastOutput.batches.getFirst().getFirst());

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

            var duplicate = scenario.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            assertEquals(RoomReplyOutcome.CREATE_REJECTED, duplicate.outcome());
            assertEquals(0, codec.createFail(scenario.lastOutput.batches.getLast().getFirst()).reason());
            var start = scenario.handle(id, PqGameSessionStartGame.TYPE_ID, root("start-empty"));

            assertEquals(RoomReplyOutcome.START_UNAVAILABLE, start.outcome());
            assertEquals(
                "Starting a match is currently unavailable.",
                new NoticeMessageCodec().decode(scenario.lastOutput.batches.getLast().getFirst()).message()
            );
            assertTrue(scenario.rooms.current(id).isPresent());

        }
    }

    @Test
    void mapsCreateFailuresLeaveOutcomesAndClosedSessionsWithoutReinitializingState() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            for (String fixture : List.of("invalid-password", "info-without-details")) {
                byte[] request = fixture.equals("info-without-details") ? new BinaryWriter()
                    .unsignedInt(PqGameSessionCreate.TYPE_ID).unsignedInt(0x2e2a05ea).unsignedShort(999)
                    .unsignedByte(0).bytes(new byte[21]).toByteArray() : RoomListingFixtures.create("Room", "");
                assertEquals(
                    RoomReplyOutcome.CREATE_REJECTED,
                    scenario.handle(id, PqGameSessionCreate.TYPE_ID, request).outcome()
                );
            }

            scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
            var result = scenario.handle(id, PqGameSessionLeave.TYPE_ID, leave(999, 0));

            assertEquals(RoomReplyOutcome.LEAVE_REJECTED, result.outcome());
            assertEquals(0, new RoomResponseCodec().leave(scenario.lastOutput.batches.getLast().getFirst()).success());
            assertEquals(RoomReplyOutcome.LEFT, scenario.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome());
            assertEquals(
                RoomReplyOutcome.LEAVE_REPLAYED,
                scenario.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome()
            );
            scenario.handler.close(id, "TEST");
            scenario.sessions.close(new SessionId(id));

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handle(id, PqGameSessionCreate.TYPE_ID, new byte[0]).outcome()
            );
            scenario.handle(id, PqGameSessionStartGame.TYPE_ID, new byte[0]);
            assertTrue(scenario.lastOutput.batches.isEmpty());
        }
    }

    @Test
    void ignoresCreationThatFinishesAfterSessionClosure() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.characters.onCommit = () -> scenario.sessions.close(new SessionId(id));

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handle(id, PqGameSessionCreate.TYPE_ID, root("create-unprotected")).outcome()
            );
            assertTrue(scenario.rooms.current(id).isEmpty());
        }
    }

    @Test
    void mapsNoItemAndRedTeamSnapshotsWithoutWireValuesInTheDomain() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var original = scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput()).room();
            var settings = new RoomSettings(original.settings().title(), RoomMode.NO_ITEM, RoomRule.BASIC, 1, 8);
            var owner = new RoomParticipant(id, original.owner().character(), 3);
            var slots = new ArrayList<>(original.slots());
            slots.set(0, new RoomSlot(0, RoomSlotState.OPEN, null));
            slots.set(2, new RoomSlot(2, RoomSlotState.OCCUPIED, owner));
            var snapshot = new RoomSnapshot(original.id(), original.generation(), 1, settings, id, slots, false);
            var result = new RoomSnapshotMapper().map(snapshot);

            assertEquals(1, result.info().details().gameType());
            assertEquals(0, result.info().details().rule());
            assertEquals(2, result.slots().get(2).occupant().team());
        }
    }

    @Test
    void discardsRepliesWhenTheSessionClosesBetweenTheActionAndResponseMapping() throws Exception {
        for (int type : new int[]{
            PqGameSessionCreate.TYPE_ID,
            PqGameSessionEnter.TYPE_ID,
            PqGameSessionQuickJoin.TYPE_ID,
            PqGameSessionLeave.TYPE_ID,
            PqGameSessionStartGame.TYPE_ID,
            PqGameSessionOpenCloseSlot.TYPE_ID,
            PqGameSessionChangeTeam.TYPE_ID,
            PqGameSessionReady.TYPE_ID
        }) {
            try (var scenario = new RoomScenario()) {
                var id = scenario.player(1, 1);
                if (type != PqGameSessionCreate.TYPE_ID) {
                    scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
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
                    scenario.enter,
                    scenario.quickJoin,
                    new ChangeRoomTeamAction(scenario.rooms),
                    new SetRoomReadyAction(scenario.rooms),
                    new SetRoomSlotAvailabilityAction(scenario.rooms),
                    scenario.leave,
                    scenario.start,
                    scenario.rooms
                );
                byte[] request = type == PqGameSessionCreate.TYPE_ID ? root("create-unprotected")
                    : type == PqGameSessionEnter.TYPE_ID ? root("enter-empty-password")
                    : type == PqGameSessionQuickJoin.TYPE_ID ? root("quickjoin-any")
                    : type == PqGameSessionLeave.TYPE_ID ? leave(1, 0)
                    : type == PqGameSessionReady.TYPE_ID ? RoomScenario.ready(0)
                    : type == PqGameSessionChangeTeam.TYPE_ID ? RoomScenario.team(2)
                    : type == PqGameSessionOpenCloseSlot.TYPE_ID ? root("PqGameSession_OpenCloseSlot-close")
                    : root("start-empty");

                assertEquals(
                    RoomReplyOutcome.IGNORED,
                    handler.handle(id, type, request, new CollectingPacketOutput()).outcome()
                );
            }
        }
    }

    @Test
    void ignoresLeaveAndStartThatDiscoverClosureDuringExecution() throws Exception {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
            scenario.channels.release(id, "CHANNEL_CLOSED");

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handle(id, PqGameSessionLeave.TYPE_ID, leave(1, 0)).outcome()
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
                scenario.enter,
                scenario.quickJoin,
                new ChangeRoomTeamAction(scenario.rooms),
                new SetRoomReadyAction(scenario.rooms),
                new SetRoomSlotAvailabilityAction(scenario.rooms),
                scenario.leave,
                scenario.start,
                scenario.rooms
            );

            assertEquals(
                RoomReplyOutcome.IGNORED,
                handler.handle(
                    id,
                    PqGameSessionStartGame.TYPE_ID,
                    root("start-empty"),
                    new CollectingPacketOutput()
                ).outcome()
            );
        }
    }

}

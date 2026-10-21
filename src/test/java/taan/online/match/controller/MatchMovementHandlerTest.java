package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.MatchScenarioTestPackets;
import taan.online.RoomScenario;
import taan.online.TestLevels;
import taan.online.match.protocol.codec.MoveCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.MoveMatchActorAction;
import taan.online.match.service.PassMatchTurnAction;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;

class MatchMovementHandlerTest {
    @Test
    void dispatchesEveryMotionKindWithoutEchoAndReturnsDeadActorsToTheRoom() throws Exception {
        try (var room = new MatchRoomFixture(new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO), 2, RoomMode.ITEM)) {
            var matches = room.scenario.matches;
            var handler = new MatchPacketHandler(
                matches,
                new TerminateMatchAction(matches),
                new ConsumeMatchResultAction(matches),
                new ConfirmMatchLoadingAction(matches),
                new ConfirmMatchPositionAction(matches),
                new ReportMatchEnemiesAction(matches),
                TestLevels.table()
            );
            var ownerOutput = new CollectingPacketOutput();
            var guestOutput = new CollectingPacketOutput();
            handler.open(room.owner, ownerOutput);
            handler.open(room.players.get(1), guestOutput);
            room.scenario.start.execute(room.owner).delivery().toCompletableFuture().join();
            var token = room.scenario.rooms.matchContext(room.owner).token();
            MatchFixtures.loaded(matches, token);
            var snapshot = matches.current(token).orElseThrow();
            var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
                .findFirst().orElseThrow();
            var id = member.sessionId();
            var output = id.equals(room.owner) ? ownerOutput : guestOutput;
            var observer = id.equals(room.owner) ? guestOutput : ownerOutput;
            var actor = snapshot.actors().get(id);
            var context = handler.capture(id);
            int before = output.batches.size();
            var move = new PcGamePlayMove(member.slotIndex(), actor.x() + 1, actor.y(), actor.facing());
            handler.handle(
                id,
                PcGamePlayMove.TYPE_ID,
                new MoveCodec().encode(move),
                context
            ).toCompletableFuture().join();
            assertEquals(before, output.batches.size());
            assertEquals(move, new MoveCodec().decode(observer.batches.getLast().getFirst()));
            handler.handle(UUID.randomUUID(), PcGamePlayMove.TYPE_ID, new MoveCodec().encode(move), context)
                .toCompletableFuture().join();
            var position = new PcGamePlaySetPos(move.slotIndex(), move.x(), move.y(), move.facing());
            handler.handle(id, PcGamePlaySetPos.TYPE_ID, new SetPosCodec().encode(position), handler.capture(id))
                .toCompletableFuture().join();
            assertEquals(position, new SetPosCodec().decode(observer.batches.getLast().getFirst()));
            var shot = MatchScenarioTestPackets.shot(member.slotIndex(), actor.facing(), 10, false);
            handler.handle(id, PcGamePlayScenario.TYPE_ID, new ScenarioCodec().encode(shot), context)
                .toCompletableFuture().join();
            byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                .unsignedByte(member.slotIndex()).unsignedByte(0).toByteArray();
            handler.handle(id, PcGamePlayEndScenario.TYPE_ID, ack, context).toCompletableFuture().join();
            var peer = room.players.stream().filter(value -> !value.equals(id)).findFirst().orElseThrow();
            handler.handle(
                peer,
                PcGamePlayEndScenario.TYPE_ID,
                ack,
                handler.capture(peer)
            ).toCompletableFuture().join();
            var current = matches.current(token).orElseThrow();
            var other = current.participants().stream().filter(value -> value.sessionId().equals(peer))
                .findFirst().orElseThrow();
            var otherPose = current.actors().get(peer);
            var nextPass = new PcGamePlayPassTurn(
                other.slotIndex(),
                otherPose.x(),
                otherPose.y(),
                otherPose.facing(),
                0
            );
            handler.handle(
                peer,
                PcGamePlayPassTurn.TYPE_ID,
                new PassTurnCodec().encode(nextPass),
                handler.capture(peer)
            )
                .toCompletableFuture().join();
            assertEquals(member.slotIndex(), matches.current(token).orElseThrow().turn().activeSlot());
            int bottom = snapshot.definitions().map().height() + 200;
            var pass = new PcGamePlayPassTurn(member.slotIndex(), move.x(), bottom, actor.facing(), 0xffffffffL);
            handler.handle(id, PcGamePlayPassTurn.TYPE_ID, new PassTurnCodec().encode(pass), handler.capture(id))
                .toCompletableFuture().join();
            assertEquals(pass, new PassTurnCodec().decode(observer.batches.getLast().getFirst()));
            room.scenario.matchTimers.advanceSeconds(1);
            assertTrue(matches.current(token).isEmpty());
            assertEquals(RoomState.WAITING, room.scenario.rooms.current(id).orElseThrow().state());
            assertTrue(room.scenario.rooms.membership(id).isPresent());
            assertTrue(output.closeReasons.isEmpty());
            assertTrue(observer.closeReasons.isEmpty());

            var old = MatchCommandContext.from(context.roomContext());
            assertFalse(new MoveMatchActorAction(matches).execute(new MoveMatchActorCommand(
                old,
                context.turnRevision(),
                context.phaseRevision(),
                member.slotIndex(),
                move.x(),
                bottom,
                0,
                MatchMovementKind.MOVE
            )).toCompletableFuture().join());
            assertFalse(new PassMatchTurnAction(matches).execute(new PassMatchTurnCommand(
                old,
                context.turnRevision(),
                context.phaseRevision(),
                member.slotIndex(),
                move.x(),
                bottom,
                0,
                0
            )).toCompletableFuture().join());
        }
    }
}

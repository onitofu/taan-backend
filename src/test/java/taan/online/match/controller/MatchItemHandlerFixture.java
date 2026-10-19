package taan.online.match.controller;

import java.util.UUID;
import taan.online.MatchRoomFixture;
import taan.online.RoomScenario;
import taan.online.MatchFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.model.MatchParticipant;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchToken;

final class MatchItemHandlerFixture implements AutoCloseable {
    final MatchRoomFixture room;
    final MatchPacketHandler handler;
    final CollectingPacketOutput owner = new CollectingPacketOutput();
    final CollectingPacketOutput guest = new CollectingPacketOutput();
    final RoomMatchToken token;

    MatchItemHandlerFixture() {
        room = new MatchRoomFixture(new RoomScenario(MatchExecutionMode.CANNON_1V1), 2, RoomMode.ITEM);
        var matches = room.scenario.matches;
        handler = new MatchPacketHandler(
            matches,
            new TerminateMatchAction(matches),
            new ConsumeMatchResultAction(matches),
            new ConfirmMatchLoadingAction(matches),
            new ConfirmMatchPositionAction(matches),
            new ReportMatchEnemiesAction(matches)
        );
        handler.open(room.owner, owner);
        handler.open(room.players.get(1), guest);
        room.scenario.start.execute(room.owner).delivery().toCompletableFuture().join();
        token = room.scenario.rooms.matchContext(room.owner).token();
        MatchFixtures.loaded(matches, token);
    }

    MatchSnapshot snapshot() {
        return room.scenario.matches.current(token).orElseThrow();
    }

    MatchParticipant active() {
        return snapshot().participants().stream().filter(p -> p.slotIndex() == snapshot().turn().activeSlot())
            .findFirst().orElseThrow();
    }

    void charge() {
        for (int index = 0; index < 20; index++) {
            var member = active();
            var actor = snapshot().actors().get(member.sessionId());
            var pass = new PcGamePlayPassTurn(member.slotIndex(), actor.x(), actor.y(), actor.facing(), 0);
            handler.handle(
                member.sessionId(),
                PcGamePlayPassTurn.TYPE_ID,
                new PassTurnCodec().encode(pass),
                handler.capture(member.sessionId())
            ).toCompletableFuture().join();
        }
    }

    void handle(UUID id, int type, byte[] root, MatchRequestContext captured) {
        handler.handle(id, type, root, captured).toCompletableFuture().join();
    }

    @Override
    public void close() {
        room.close();
    }
}

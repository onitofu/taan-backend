package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchBootstrapFixture;
import taan.online.MatchFixtures;
import taan.online.match.model.MatchOutcome;
import taan.online.match.protocol.codec.MoveCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchTurnTransition;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class ProtocolMatchViewTest {
    @Test
    void refusesRewardedAndInconsistentOutcomes() {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output);

        assertThrows(
            IllegalArgumentException.class,
            () -> view.end(new MatchOutcome(Set.of(RoomTeam.BLUE, RoomTeam.RED), false))
        );
        assertThrows(IllegalArgumentException.class, () -> view.end(new MatchOutcome(Set.of(), true)));
        assertEquals(0, output.batches.size());
    }

    @Test
    void sendsTheIndependentFullNoAwardGoldenAndKeepsItsTemplateImmutable() throws Exception {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output);
        byte[] expected = MatchFixtures.root("end-game-result0-no-awards");
        view.end(MatchOutcome.previewAborted()).toCompletableFuture().join();

        assertEquals(406, expected.length);
        assertEquals(1, output.batches.getFirst().size());
        assertArrayEquals(expected, output.batches.getFirst().getFirst());
        output.batches.getFirst().getFirst()[0] = 0;
        view.end(MatchOutcome.previewAborted());

        assertArrayEquals(expected, output.batches.getLast().getFirst());
        view.close("TEST_CLOSE");

        assertEquals(List.of("TEST_CLOSE"), output.closeReasons);
    }

    @Test
    void mapsMotionAndSeparatesWatchdogStopFromTheFollowingTurnStart() {
        try (var fixture = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            fixture.start().delivery().toCompletableFuture().join();
            var snapshot = fixture.snapshot();
            var output = new CollectingPacketOutput();
            var view = new ProtocolMatchView(output);
            view.nextTurn(new MatchTurnTransition(0, snapshot, true));
            assertEquals(0, new TurnEndCodec().decode(output.batches.getLast().getFirst()).slotIndex());
            view.nextTurn(new MatchTurnTransition(0, snapshot, false));
            assertEquals(1, output.batches.getLast().size());
            assertEquals(
                snapshot.turn().activeSlot(),
                new TurnStartCodec().decode(
                    output.batches.getLast().getFirst()
                ).activeSlot()
            );
            view.stopTurn(2);
            assertEquals(2, new TurnEndCodec().decode(output.batches.getLast().getFirst()).slotIndex());
            view.movement(new MatchMovementNotice(MatchMovementKind.MOVE, 0, 100, 200, 1, 0));
            assertEquals(100, new MoveCodec().decode(output.batches.getLast().getFirst()).x());
            view.movement(new MatchMovementNotice(MatchMovementKind.SET_POS, 2, 300, 400, 0, 0));
            assertEquals(400, new SetPosCodec().decode(output.batches.getLast().getFirst()).y());
            view.movement(new MatchMovementNotice(MatchMovementKind.PASS_TURN, 2, 300, 400, 1, 0xffffffffL));
            assertEquals(0xffffffffL, new PassTurnCodec().decode(output.batches.getLast().getFirst()).scalar20());
        }
    }
}

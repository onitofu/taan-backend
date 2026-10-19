package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchFixtures;
import taan.online.match.model.MatchOutcome;
import taan.online.room.model.RoomTeam;

class ProtocolMatchViewTest {
    @Test
    void refusesOutcomesWhoseRewardAndWinnerContractHasNotBeenImplemented() {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output);

        assertThrows(IllegalArgumentException.class, () -> view.end(new MatchOutcome(Set.of(RoomTeam.BLUE), false)));
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
}

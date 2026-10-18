package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.MatchFixtures;

class ProtocolMatchViewTest {
    @Test
    void sendsTheIndependentFullNoAwardGoldenAndKeepsItsTemplateImmutable() throws Exception {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output);
        byte[] expected = MatchFixtures.root("end-game-result0-no-awards");
        view.endPreview().toCompletableFuture().join();

        assertEquals(406, expected.length);
        assertEquals(1, output.batches.getFirst().size());
        assertArrayEquals(expected, output.batches.getFirst().getFirst());
        output.batches.getFirst().getFirst()[0] = 0;
        view.endPreview();

        assertArrayEquals(expected, output.batches.getLast().getFirst());
        view.close("TEST_CLOSE");

        assertEquals(List.of("TEST_CLOSE"), output.closeReasons);
    }
}

package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.TestLevels;
import taan.online.TestMatchResults;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.room.model.RoomTeam;

class ProtocolMatchOutcomeViewTest {
    @Test
    void sendsBothIndependentWinnerFramesWithEightEmptyAwardContainers() throws Exception {
        for (var team : RoomTeam.values()) {
            int winner = team == RoomTeam.BLUE ? 1 : 2;
            byte[] frame;
            String name = "/match/EndGame-result" + winner + "-no-awards.frame.hex";

            try (var input = getClass().getResourceAsStream(name)) {
                String hex = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
                frame = HexFormat.of().parseHex(hex.replaceAll("\\s+", ""));
            }

            byte[] root = Arrays.copyOfRange(frame, 4, frame.length);
            var output = new CollectingPacketOutput();
            new ProtocolMatchView(output, TestLevels.table()).end(
                TestMatchResults.end(new MatchOutcome(Set.of(team), false, MatchOutcomeCause.ELIMINATION))
            );
            assertArrayEquals(root, output.batches.getFirst().getLast());
            var packet = new EndGameCodec().decode(root);
            assertEquals(winner, packet.result().resultKind());
            assertEquals(8, packet.result().slots().size());
            assertEquals(0, packet.packetFlag());
        }
    }
}

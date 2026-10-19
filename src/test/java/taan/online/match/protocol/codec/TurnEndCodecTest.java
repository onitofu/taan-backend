package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.PcGamePlayTurnEnd;
import taan.online.protocol.MalformedPacketException;

class TurnEndCodecTest {
    @Test
    void matchesIndependentGoldenAndRejectsEveryTruncationInvalidSlotAndUnsupportedEffect() throws Exception {
        byte[] frame;

        try (var input = getClass().getResourceAsStream("/match/turn-end-slot0-no-effect.frame.hex")) {
            frame = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s", ""));
        }

        var root = Arrays.copyOfRange(frame, 4, frame.length);
        var codec = new TurnEndCodec();
        assertEquals(new PcGamePlayTurnEnd(0, 0), codec.decode(root));
        assertArrayEquals(root, codec.encode(new PcGamePlayTurnEnd(0, 0)));

        for (int length = 0; length < root.length; length++) {
            var truncated = Arrays.copyOf(root, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        var invalid = root.clone();
        invalid[4] = 8;
        assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        invalid[4] = 0;
        invalid[9] = 1;
        assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
    }
}

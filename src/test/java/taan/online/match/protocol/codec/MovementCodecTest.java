package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.protocol.MalformedPacketException;

class MovementCodecTest {
    @Test
    void matchesIndependentMoveAndPassRootsAndRejectsMalformedInputs() throws Exception {
        var move = new MoveCodec();
        var pass = new PassTurnCodec();
        byte[] moveRoot = root("move-slot0-x352-y676-right.frame.hex");
        byte[] passRoot = root("pass-turn-slot0-scalar0.frame.hex");
        assertEquals(new PcGamePlayMove(0, 352, 676, 1), move.decode(moveRoot));
        assertArrayEquals(moveRoot, move.encode(new PcGamePlayMove(0, 352, 676, 1)));
        assertEquals(new PcGamePlayPassTurn(0, 336, 676, 0, 0), pass.decode(passRoot));
        assertArrayEquals(passRoot, pass.encode(new PcGamePlayPassTurn(0, 336, 676, 0, 0)));

        for (int size = 0; size < passRoot.length; size++) {
            var truncated = Arrays.copyOf(passRoot, size);
            assertThrows(MalformedPacketException.class, () -> pass.decode(truncated));
        }

        for (int size = 0; size < moveRoot.length; size++) {
            var truncated = Arrays.copyOf(moveRoot, size);
            assertThrows(MalformedPacketException.class, () -> move.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> move.decode(Arrays.copyOf(moveRoot, 15)));
        assertThrows(MalformedPacketException.class, () -> pass.decode(Arrays.copyOf(passRoot, 19)));
        moveRoot[4] = 8;
        passRoot[13] = 2;
        assertThrows(MalformedPacketException.class, () -> move.decode(moveRoot));
        assertThrows(MalformedPacketException.class, () -> pass.decode(passRoot));
        assertThrows(MalformedPacketException.class, () -> move.encode(new PcGamePlayMove(0, 0, 0, 2)));
        assertThrows(MalformedPacketException.class, () -> pass.encode(new PcGamePlayPassTurn(8, 0, 0, 0, 0)));
        var scalar = new PcGamePlayPassTurn(2, -1, Integer.MIN_VALUE, 1, 0xffffffffL);
        assertEquals(scalar, pass.decode(pass.encode(scalar)));
    }

    private byte[] root(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/match/" + name)) {
            byte[] frame = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s", ""));
            return Arrays.copyOfRange(frame, 4, frame.length);
        }
    }
}

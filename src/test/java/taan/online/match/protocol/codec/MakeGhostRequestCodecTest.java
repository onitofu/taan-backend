package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.PqGamePlayMakeGhost;
import taan.online.protocol.MalformedPacketException;

class MakeGhostRequestCodecTest {
    @Test
    void decodesTheIndependentNativeRequestAndPreservesEveryValidSlot() throws Exception {
        byte[] root;
        String provenance;

        try (
            var input = getClass().getResourceAsStream("/match/ghost/00144-c2s-combat.hex");
            var manifest = getClass().getResourceAsStream("/match/ghost/provenance.json")
        ) {
            root = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s+", ""));
            provenance = new String(manifest.readAllBytes(), StandardCharsets.UTF_8);
        }

        var codec = new MakeGhostRequestCodec();
        assertEquals(0, codec.decode(root).slotIndex());
        assertArrayEquals(root, codec.encode(new PqGamePlayMakeGhost(0)));
        assertTrue(provenance.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root))));

        for (int slot = 0; slot < 8; slot++) {
            var packet = new PqGamePlayMakeGhost(slot);
            assertEquals(packet, codec.decode(codec.encode(packet)));
        }

        for (int size = 0; size < root.length; size++) {
            byte[] shortRoot = Arrays.copyOf(root, size);
            assertThrows(MalformedPacketException.class, () -> codec.decode(shortRoot));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, 6)));
        var unknown = root.clone();
        unknown[0]++;
        assertThrows(MalformedPacketException.class, () -> codec.decode(unknown));

        for (int slot : new int[]{-1, 8, 255}) {
            assertThrows(MalformedPacketException.class, () -> codec.encode(new PqGamePlayMakeGhost(slot)));
        }

        root[4] = 8;
        byte[] badSlot = root;
        assertThrows(MalformedPacketException.class, () -> codec.decode(badSlot));
    }
}

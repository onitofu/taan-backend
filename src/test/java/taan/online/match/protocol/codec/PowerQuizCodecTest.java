package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.codec.RoomResponseCodec;

class PowerQuizCodecTest {
    @Test
    void readsIndependentClientClaimsIncludingOpaqueFieldsAndChecksTheStoredHashes() throws Exception {
        String provenance;

        try (var input = getClass().getResourceAsStream("/match/powerquiz/provenance.json")) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var codec = new EatBonusItemCodec();
        var names = List.of("item-02", "item-03-repeat", "item-05-third-slot", "item-06-full");
        var items = List.of(4, 7, 1005, 1015);

        for (int index = 0; index < names.size(); index++) {
            byte[] root = MatchFixtures.root("powerquiz/" + names.get(index));
            var message = codec.decode(root);
            assertEquals(2, message.slotIndex());
            assertEquals(2, message.command().header().slot());
            assertEquals(-1L, message.command().entityId());
            assertEquals(items.get(index), message.command().itemId());
            assertTrue(message.gauge().isEmpty());
            assertArrayEquals(root, codec.encode(message));
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
            assertTrue(provenance.contains(hash));

            for (int size = 0; size < root.length; size++) {
                byte[] truncated = Arrays.copyOf(root, size);
                assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
            }
        }

        assertEquals(1062469632, codec.decode(MatchFixtures.root("powerquiz/item-02")).command().header().tick());
        assertEquals(5921112, codec.decode(MatchFixtures.root("powerquiz/item-06-full")).command().opaque24());
        byte[] init = MatchFixtures.root("powerquiz/PcGameSession_Init-rule1");
        var initialized = new RoomResponseCodec().initialize(init);
        assertEquals(1, initialized.info().details().rule());
        assertArrayEquals(init, new RoomResponseCodec().encode(initialized));
        assertTrue(provenance.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(init))));
    }
}

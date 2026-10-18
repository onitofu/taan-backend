package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.option.packet.OptionList;

class RoomGameplayOptionsCodecTest {
    @Test
    void preservesIndependentGameplayPreludesWithRequiredEmptyOptionLists() throws Exception {
        var codec = new RoomResponseCodec();

        for (int index : new int[]{0, 2}) {
            byte[] root = fixture("slot" + index + "-gameplay-prelude.root.hex");
            var message = codec.slot(root);

            assertEquals(new OptionList(), message.slot().occupant().options());
            assertEquals(index, message.slot().slotIndex());
            assertEquals(0, message.resetWidget());
            assertEquals("", message.text());
            assertArrayEquals(root, codec.encode(message));

            for (int length = 0; length < root.length; length++) {
                byte[] truncated = Arrays.copyOf(root, length);

                assertThrows(MalformedPacketException.class, () -> codec.slot(truncated));
            }

            assertThrows(MalformedPacketException.class, () -> codec.slot(Arrays.copyOf(root, root.length + 1)));
        }
    }

    @Test
    void rejectsInvalidPresenceTypeAndNonemptyOptionsBeforePublishingASlot() throws Exception {
        var codec = new RoomResponseCodec();
        byte[] root = fixture("slot0-gameplay-prelude.root.hex");
        int presence = root.length - 13;
        byte[] brokenPresence = root.clone();
        brokenPresence[presence] = 2;

        assertThrows(MalformedPacketException.class, () -> codec.slot(brokenPresence));

        for (int offset : new int[]{presence + 1, presence + 5}) {
            byte[] broken = root.clone();
            ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1);

            assertThrows(MalformedPacketException.class, () -> codec.slot(broken));
        }
    }

    private byte[] fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/match/" + name)) {
            return HexFormat.of().parseHex(
                new String(stream.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }
}

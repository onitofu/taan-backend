package taan.online.room.protocol.codec;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.reader;

import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.packet.GameSessionInfoPack;

class GameSessionInfoPackCodecTest {
    @Test
    void selectsEachFieldByItsActualBitWithoutSerializingOtherValues() {
        var bodies = Map.ofEntries(
            entry(0x0001, "01"),
            entry(0x0200, "09"),
            entry(0x0002, "0700000052006f006f006d0020003dd800de"),
            entry(0x0100, "01"),
            entry(0x0004, "3412"),
            entry(0x0080, "03"),
            entry(0x0008, "04"),
            entry(0x0010, "08"),
            entry(0x0040, "7856"),
            entry(0x0020, "01040000003a043b044e044704")
        );
        var codec = new GameSessionInfoPackCodec();

        for (var entry : bodies.entrySet()) {
            int mask = entry.getKey();
            var pack = new GameSessionInfoPack(mask, 321, 1, 9, "Room 😀", 1, 0x1234, 3, 4, 8, 0x5678, "ключ");
            var output = new BinaryWriter();
            codec.encode(output, pack);
            String header = "69076649" + String.format("%02x%02x", mask & 255, mask >>> 8) + "4101";
            byte[] expected = HexFormat.of().parseHex(header + entry.getValue());

            assertArrayEquals(expected, output.toByteArray());
            var input = reader(expected);

            assertEquals(mask, codec.decode(input).mask());
            input.finish();
        }
    }
}

package taan.online.character.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

class InventoryProfileCodecTest {
    @Test
    void encodesQuietGoldUpdatesAgainstIndependentGoldenBytes() {
        var codec = new UpdateMoneyCodec();
        var zero = HexFormat.of().parseHex("1e059b220000000000000000000000");
        var maximum = HexFormat.of().parseHex("1e059b220000ffffffff0000000000");

        assertArrayEquals(zero, codec.encode(new PcUpdateMoney(0, 0, 0, 0, 0)));
        assertArrayEquals(maximum, codec.encode(new PcUpdateMoney(0, 0, 0xffff_ffffL, 0, 0)));
        assertEquals(new PcUpdateMoney(0, 0, 0xffff_ffffL, 0, 0), codec.decode(maximum));

        for (int length = 0; length < maximum.length; length++) {
            byte[] truncated = Arrays.copyOf(maximum, length);

            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(new byte[15]));
        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(maximum, 16)));
    }

    @Test
    void keepsTheMaximumLevelProgressBarNonzeroAndAllMedalsPresent() {
        var initial = CharacterFixtures.character(3, new AccountId(1), AccountGender.MALE, "Hero");
        var character = new GameCharacter(
            initial.id(),
            initial.accountId(),
            initial.name(),
            109,
            initial.gender(),
            initial.appearance(),
            initial.createdAt(),
            new CharacterProgress(1048576, 0xffff_ffffL, 0, 1),
            0xffff_ffffL
        );
        var info = new CharacterCatalogMapper(TestLevels.table()).map(List.of(character)).info().getFirst();
        var output = new BinaryWriter();
        new CharacterInfoCodec().encode(output, info);

        assertEquals(109, info.level());
        assertEquals(1048576, info.currentExp());
        assertEquals(1034050, info.levelStartExp());
        assertEquals(1048576, info.nextLevelExp());
        assertEquals(0xffff_ffffL, info.forceMedals());
        assertEquals(0xffff_ffffL, info.gold());
        byte[] bytes = output.toByteArray();

        assertArrayEquals(
            HexFormat.of().parseHex("ffffffff000000000100000000000000"),
            Arrays.copyOfRange(bytes, bytes.length - 16, bytes.length)
        );
    }

    @Test
    void matchesTheIndependentResearchMoneyFrame() throws Exception {
        byte[] frame;

        try (var input = getClass().getResourceAsStream(
            "/currency/PcUpdateMoney-gold-100000.synthetic.frame.hex"
        )) {
            frame = HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }

        assertEquals(19, frame.length);
        assertEquals(15, frame[0]);
        assertArrayEquals(
            Arrays.copyOfRange(frame, 4, frame.length),
            new UpdateMoneyCodec().encode(new PcUpdateMoney(0, 0, 100000, 0, 0))
        );
    }

    @Test
    void sendsEveryExperienceAndMedalFieldAndPersistentGoldInCharacterInfo() {
        var initial = CharacterFixtures.character(3, new AccountId(1), AccountGender.MALE, "Abcd");
        var character = new GameCharacter(
            initial.id(),
            initial.accountId(),
            initial.name(),
            10,
            initial.gender(),
            initial.appearance(),
            initial.createdAt(),
            new CharacterProgress(26000, 4, 5, 6),
            123456
        );
        var info = new CharacterCatalogMapper(TestLevels.table()).map(List.of(character)).info().getFirst();
        var output = new BinaryWriter();
        new CharacterInfoCodec().encode(output, info);
        byte[] bytes = output.toByteArray();
        var expectedPrefix = HexFormat.of().parseHex(
            "1905d422030000000400000041006200630064000a00906500008e6200006e73000040e2010001"
        );

        assertArrayEquals(expectedPrefix, Arrays.copyOf(bytes, expectedPrefix.length));
        assertArrayEquals(
            HexFormat.of().parseHex("04000000050000000600000000000000"),
            Arrays.copyOfRange(bytes, bytes.length - 16, bytes.length)
        );
        assertEquals(26000, info.currentExp());
        assertEquals(TestLevels.table().displayNext(10), info.nextLevelExp());
    }
}

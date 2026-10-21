package taan.online.character.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.character.protocol.packet.CharacterCatalog;
import taan.online.character.protocol.packet.CharacterPart;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PrConfirmName;
import taan.online.character.protocol.packet.PrNewCharacter;
import taan.online.protocol.MalformedPacketException;

class CharacterCodecTest {
    @Test
    void readsNativeRequestsAndMatchesBothNondefaultCatalogsAcceptedByTheClient() {
        var confirm = new ConfirmNameRequestCodec().decode(CharacterFixtures.root("event-0015-request.root.hex"));

        assertEquals(0, confirm.localSlot());
        assertEquals("MaleHero", confirm.name());
        assertFalse(confirm.toString().contains(confirm.name()));
        assertArrayEquals(
            CharacterFixtures.root("event-0017-reply.frame.hex"),
            new ConfirmNameResponseCodec().encode(new PrConfirmName(0))
        );
        assertArrayEquals(
            CharacterFixtures.root("event-0073-reply.frame.hex"),
            new ConfirmNameResponseCodec().encode(new PrConfirmName(1))
        );

        for (var gender : AccountGender.values()) {
            boolean male = gender == AccountGender.MALE;
            String filename = male ? "event-0023-request.root.hex" : "event-0081-request.root.hex";
            var request = new CreateCharacterRequestCodec().decode(CharacterFixtures.root(filename));
            var character = CharacterFixtures.character(
                male ? 1001 : 1002,
                new AccountId(male ? 101 : 102),
                gender,
                male ? "MaleHero" : "Luma"
            );
            var catalog = new CharacterCatalogMapper(TestLevels.table()).map(List.of(character));
            var encoded = new CreateCharacterResponseCodec().encode(new PrNewCharacter(0, 0, catalog));

            assertEquals(character.name().value(), request.name());
            assertEquals(16, request.parts().size());
            assertFalse(request.toString().contains(request.name()));
            assertFalse(catalog.toString().contains(request.name()));
            assertArrayEquals(
                CharacterFixtures.withProgress(
                    CharacterFixtures.root(male ? "event-0025-reply.frame.hex" : "event-0083-reply.frame.hex"),
                    0,
                    0,
                    940
                ),
                encoded
            );
        }

        assertArrayEquals(
            CharacterFixtures.root("event-0054-reply.frame.hex"),
            new CreateCharacterResponseCodec().encode(new PrNewCharacter(1, 0, null))
        );
        assertArrayEquals(
            CharacterFixtures.root("event-0060-reply.frame.hex"),
            new CreateCharacterResponseCodec().encode(new PrNewCharacter(2, 0, null))
        );
    }

    @Test
    void rejectsEveryTruncatedRequestWrongTypesMalformedUtf16AndTrailingBytes() {
        byte[] confirm = CharacterFixtures.root("event-0015-request.root.hex");
        byte[] create = CharacterFixtures.root("event-0023-request.root.hex");
        var confirmCodec = new ConfirmNameRequestCodec();
        var createCodec = new CreateCharacterRequestCodec();

        for (int length = 0; length < confirm.length; length++) {
            byte[] truncated = Arrays.copyOf(confirm, length);

            assertThrows(MalformedPacketException.class, () -> confirmCodec.decode(truncated));
        }

        for (int length = 0; length < create.length; length++) {
            byte[] truncated = Arrays.copyOf(create, length);

            assertThrows(MalformedPacketException.class, () -> createCodec.decode(truncated));
        }

        for (byte[] root : List.of(confirm, create)) {
            byte[] wrongType = root.clone();
            wrongType[0] ^= 1;
            byte[] trailing = Arrays.copyOf(root, root.length + 1);
            byte[] invalidString = root.clone();
            ByteBuffer.wrap(invalidString).order(ByteOrder.LITTLE_ENDIAN).putInt(5, -1);

            if (root == confirm) {
                assertThrows(MalformedPacketException.class, () -> confirmCodec.decode(wrongType));
                assertThrows(MalformedPacketException.class, () -> confirmCodec.decode(trailing));
                assertThrows(MalformedPacketException.class, () -> confirmCodec.decode(invalidString));
            } else {
                assertThrows(MalformedPacketException.class, () -> createCodec.decode(wrongType));
                assertThrows(MalformedPacketException.class, () -> createCodec.decode(trailing));
                assertThrows(MalformedPacketException.class, () -> createCodec.decode(invalidString));
            }
        }

        byte[] surrogate = confirm.clone();
        ByteBuffer.wrap(surrogate).order(ByteOrder.LITTLE_ENDIAN).putShort(9, (short) 0xd800);

        assertThrows(MalformedPacketException.class, () -> confirmCodec.decode(surrogate));
    }

    @Test
    void preservesUnsignedValuesAndImmutableMatchingCatalogVectors() {
        byte[] root = CharacterFixtures.root("event-0023-request.root.hex");
        int firstPart = 9 + 2 * "MaleHero".length();
        ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).putInt(firstPart, -1)
            .putShort(firstPart + 4, (short) -1).putShort(firstPart + 6, (short) -1)
            .putShort(firstPart + 8, (short) -1);
        var request = new CreateCharacterRequestCodec().decode(root);

        assertEquals(new CharacterPart(0xffff_ffffL, 65535, 65535, 65535), request.parts().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> request.parts().clear());
        assertThrows(IllegalArgumentException.class, () -> new CharacterAppear(1, 0, 0, List.of()));

        var parts = new ArrayList<>(request.parts());
        var copy = new PqNewCharacter(0, request.name(), parts);
        parts.clear();

        assertEquals(16, copy.parts().size());

        var appearance = new CharacterAppear(1, 0, 0, request.parts());

        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterCatalog(List.of(), List.of(appearance))
        );

        var empty = new CharacterCatalogMapper(TestLevels.table()).map(List.of());

        assertTrue(empty.info().isEmpty());
        assertEquals(12, new CharacterCatalogCodec().encode(empty).length);
        assertThrows(UnsupportedOperationException.class, () -> empty.appearances().add(appearance));
    }
}

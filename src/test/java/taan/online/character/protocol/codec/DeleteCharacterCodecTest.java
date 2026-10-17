package taan.online.character.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.character.protocol.packet.CharacterCatalog;
import taan.online.character.protocol.packet.PrDelCharacter;
import taan.online.protocol.MalformedPacketException;

class DeleteCharacterCodecTest {
    @Test
    void matchesNativeRequestsAndIndependentEmptyRemainingAndFailureReplies() {
        var codec = new DeleteCharacterRequestCodec();
        var request = codec.decode(CharacterFixtures.root("event-0033-request.root.hex"));

        assertEquals(0, request.localSlot());
        assertEquals(1001, request.characterId());
        assertEquals(2001, codec.decode(CharacterFixtures.root("event-0091-request.root.hex")).characterId());
        assertEquals(0xffff_ffffL, codec.decode(CharacterFixtures.delete(0xffff_ffffL, 255)).characterId());
        assertEquals(255, codec.decode(CharacterFixtures.delete(0, 255)).localSlot());

        var encoder = new DeleteCharacterResponseCodec();

        assertArrayEquals(
            CharacterFixtures.root("event-0035-reply.frame.hex"),
            encoder.encode(new PrDelCharacter(1, 0, null))
        );
        assertArrayEquals(
            CharacterFixtures.root("event-0047-reply.frame.hex"),
            encoder.encode(new PrDelCharacter(0, 0, new CharacterCatalog(List.of(), List.of())))
        );

        var remaining = CharacterFixtures.character(1002, new AccountId(102), AccountGender.FEMALE, "Luma");

        assertArrayEquals(
            CharacterFixtures.root("event-0093-reply.frame.hex"),
            encoder.encode(new PrDelCharacter(0, 0, new CharacterCatalogMapper().map(List.of(remaining))))
        );
    }

    @Test
    void rejectsAllTruncationsWrongTypeAndTrailingBytes() {
        var codec = new DeleteCharacterRequestCodec();
        byte[] root = CharacterFixtures.root("event-0033-request.root.hex");

        for (int length = 0; length < root.length; length++) {
            byte[] truncated = Arrays.copyOf(root, length);

            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        byte[] wrongType = root.clone();
        wrongType[0] ^= 1;

        assertThrows(MalformedPacketException.class, () -> codec.decode(wrongType));
        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
    }
}

package taan.online;

import java.io.IOException;
import taan.online.inventory.model.StarterEquipment;
import taan.online.inventory.model.StarterItem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.bootstrap.CharacterDefinitionLoader;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterCreationOptions;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.CharacterNameFilter;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.codec.CreateCharacterRequestCodec;

public final class CharacterFixtures {
    private CharacterFixtures() {
    }

    public static byte[] root(String filename) {
        try (var input = CharacterFixtures.class.getResourceAsStream("/character/" + filename)) {
            byte[] value = HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", "")
            );
            return filename.contains(".frame.") ? Arrays.copyOfRange(value, 4, value.length) : value;
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    public static CharacterNameFilter filter() {
        return new CharacterDefinitionLoader().nameFilter(
            CharacterFixtures.class.getResourceAsStream("/character/name-filters.txt")
        );
    }

    public static CharacterCreationOptions options() {
        return new CharacterDefinitionLoader().creationOptions(
            CharacterFixtures.class.getResourceAsStream("/character/creation-options.csv")
        );
    }

    public static CharacterAppearance appearance(AccountGender gender) {
        var request = new CreateCharacterRequestCodec().decode(
            root(gender == AccountGender.MALE ? "event-0023-request.root.hex" : "event-0081-request.root.hex")
        );
        var parts = new ArrayList<AppearancePart>();

        for (int slot = 0; slot < request.parts().size(); slot++) {
            var part = request.parts().get(slot);
            parts.add(new AppearancePart(slot, part.itemId(), part.parameter0(), part.parameter1(), part.parameter2()));
        }

        return new CharacterAppearance(parts);
    }

    public static GameCharacter character(long id, AccountId owner, AccountGender gender, String name) {
        return new GameCharacter(
            new CharacterId(id),
            owner,
            new CharacterName(name),
            1,
            gender,
            appearance(gender),
            Instant.EPOCH
        );
    }

    public static StarterEquipment starter(AccountGender gender) {
        return new StarterEquipment(
            appearance(gender).parts().stream().filter(part -> part.itemId() != 0)
                .map(
                    part -> new StarterItem(
                        part.slot(),
                        part.itemId(),
                        part.parameter0(),
                        part.parameter1(),
                        part.parameter2()
                    )
                ).toList()
        );
    }

    public static byte[] select(long characterId) {
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x3abb06ae).putInt(1).putInt((int) characterId).array();
    }

    public static byte[] delete(long characterId, int slot) {
        return ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x26d30563).put((byte) slot).putInt((int) characterId).array();
    }

    public static byte[] confirm(String name, int slot) {
        byte[] text = name.getBytes(StandardCharsets.UTF_16LE);
        return ByteBuffer.allocate(9 + text.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x22e70510).put((byte) slot).putInt(name.length()).put(text).array();
    }

    public static byte[] create(String name, AccountGender gender, int slot) {
        return create(name, appearance(gender), slot);
    }

    public static byte[] create(String name, CharacterAppearance appearance, int slot) {
        byte[] text = name.getBytes(StandardCharsets.UTF_16LE);
        var output = ByteBuffer.allocate(169 + text.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x27b90578).put((byte) slot).putInt(name.length()).put(text);

        for (var part : appearance.parts()) {
            output.putInt((int) part.itemId()).putShort((short) part.parameter0())
                .putShort((short) part.parameter1()).putShort((short) part.parameter2());
        }

        return output.array();
    }
}

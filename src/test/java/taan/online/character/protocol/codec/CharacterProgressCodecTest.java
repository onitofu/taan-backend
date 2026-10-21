package taan.online.character.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.PostmatchFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.GameCharacter;
import taan.online.protocol.BinaryWriter;

class CharacterProgressCodecTest {
    @Test
    void restoresPersistentMedalCountsInTheVerifiedProfileFieldsWithoutApplyingThemAgain() throws Exception {
        var original = CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Hero1");
        var character = new GameCharacter(
            original.id(),
            original.accountId(),
            original.name(),
            original.level(),
            original.gender(),
            original.appearance(),
            original.createdAt(),
            new CharacterProgress(700, 1, 2, 3)
        );
        var info = new CharacterCatalogMapper().map(List.of(character)).info().getFirst();
        var output = new BinaryWriter();
        new CharacterInfoCodec().encode(output, info);

        assertEquals(1, info.forceMedals());
        assertEquals(2, info.killMedals());
        assertEquals(3, info.supplyMedals());
        assertEquals(700, character.progress().experience());
        assertArrayEquals(PostmatchFixtures.bytes("character-info-progress.root.hex"), output.toByteArray());
    }
}

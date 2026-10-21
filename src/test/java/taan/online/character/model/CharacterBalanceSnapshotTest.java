package taan.online.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

class CharacterBalanceSnapshotTest {
    @Test
    void acceptsUnsignedBalancesAndRequiresCharacterIdentity() {
        var value = new CharacterBalanceSnapshot(new CharacterId(3), 0, 0xffff_ffffL);

        assertEquals(0, value.cash());
        assertEquals(0xffff_ffffL, value.gold());
        assertThrows(NullPointerException.class, () -> new CharacterBalanceSnapshot(null, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "4294967296,0", "0,-1", "0,4294967296"})
    void rejectsBalancesOutsideTheWireRange(long cash, long gold) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterBalanceSnapshot(new CharacterId(3), cash, gold)
        );
    }

    @ParameterizedTest
    @CsvSource({"-1", "4294967296", "0", "4294967295"})
    void validatesGoldInThePersistentCharacter(long gold) {
        var original = CharacterFixtures.character(3, new AccountId(1), AccountGender.MALE, "Owner");

        if (gold < 0 || gold > 0xffff_ffffL) {
            assertThrows(IllegalArgumentException.class, () -> copy(original, gold));
        } else {
            assertEquals(gold, copy(original, gold).gold());
        }
    }

    private GameCharacter copy(GameCharacter character, long gold) {
        return new GameCharacter(
            character.id(),
            character.accountId(),
            character.name(),
            character.level(),
            character.gender(),
            character.appearance(),
            character.createdAt(),
            character.progress(),
            gold
        );
    }
}

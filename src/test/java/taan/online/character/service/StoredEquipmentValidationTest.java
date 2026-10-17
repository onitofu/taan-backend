package taan.online.character.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.InventoryFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.TestCharacterRepository;

class StoredEquipmentValidationTest {
    @Test
    void refusesToSendUnavailableEquipmentToTheClient() {
        var repository = new TestCharacterRepository();
        var character = CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "MaleHero");
        var parts = character.appearance().parts().stream().map(
            part -> part.slot() == 1
                ? new AppearancePart(1, 636, 0, 0, 0) : part
        ).toList();
        repository.stored.add(
            new GameCharacter(
                character.id(),
                character.accountId(),
                character.name(),
                character.level(),
                character.gender(),
                new CharacterAppearance(parts),
                character.createdAt()
            )
        );
        var action = new ListCharactersAction(repository, InventoryFixtures.catalog());

        assertThrows(IllegalArgumentException.class, () -> action.execute(new AccountId(1)));
    }
}

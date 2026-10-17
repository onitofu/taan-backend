package taan.online.character.repository;

import java.util.List;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.StarterEquipment;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterName;
import taan.online.character.model.CharacterId;
import taan.online.character.model.GameCharacter;

public interface CharacterTransaction extends AutoCloseable {
    AccountGender accountGender();

    boolean hasCharacters();

    Optional<GameCharacter> tryInsert(CharacterName name, CharacterAppearance appearance, StarterEquipment starter);

    List<CharacterInventory> inventories(CharacterId characterId);

    boolean archive(CharacterId characterId);

    List<GameCharacter> catalog();

    void commit();

    @Override
    void close();
}

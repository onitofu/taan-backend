package taan.online.character.repository;

import java.util.List;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.StarterEquipment;

public interface CharacterTransaction extends AutoCloseable {
    AccountGender accountGender();

    boolean hasCharacters();

    Optional<CharacterId> tryInsert(CharacterName name, StarterEquipment starter);

    List<CharacterInventory> inventories(CharacterId characterId);

    boolean archive(CharacterId characterId);

    List<GameCharacter> catalog();

    void commit();

    @Override
    void close();
}

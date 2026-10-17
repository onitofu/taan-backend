package taan.online.character.repository;

import java.util.List;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

public interface CharacterCreationTransaction extends AutoCloseable {
    AccountGender accountGender();

    boolean hasCharacters();

    Optional<GameCharacter> tryInsert(CharacterName name, CharacterAppearance appearance);

    List<GameCharacter> catalog();

    void commit();

    @Override
    void close();
}

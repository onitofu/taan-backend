package taan.online.character.repository;

import java.util.List;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

public interface CharacterRepository {
    boolean existsByName(CharacterName name);

    List<GameCharacter> findByAccount(AccountId accountId);

    CharacterTransaction beginTransaction(AccountId accountId);
}

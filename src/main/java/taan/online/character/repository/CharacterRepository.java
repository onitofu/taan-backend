package taan.online.character.repository;

import java.util.List;
import java.util.Optional;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterBalanceSnapshot;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

public interface CharacterRepository {
    Optional<CharacterBalanceSnapshot> findBalances(AccountId accountId, CharacterId characterId);

    boolean existsByName(CharacterName name);

    List<GameCharacter> findByAccount(AccountId accountId);

    CharacterTransaction beginTransaction(AccountId accountId);
}

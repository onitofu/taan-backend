package taan.online.character.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

public final class TestCharacterRepository implements CharacterRepository {
    public final List<GameCharacter> stored = new ArrayList<>();
    public AccountGender gender = AccountGender.FEMALE;
    public boolean conflict;
    public RuntimeException failure;
    public Runnable onRead = () -> { };
    public Runnable onBegin = () -> { };
    public Runnable onInsert = () -> { };
    public Runnable onCommit = () -> { };
    public int reads;
    public int checks;
    public int transactions;
    public int inserts;
    public int commits;
    public int rollbacks;
    public int closes;

    @Override
    public boolean existsByName(CharacterName name) {
        checks++;
        onRead.run();
        fail();
        return stored.stream().anyMatch(character -> character.name().equals(name));
    }

    @Override
    public List<GameCharacter> findByAccount(AccountId owner) {
        reads++;
        onRead.run();
        fail();
        return stored.stream().filter(character -> character.accountId().equals(owner))
            .sorted(Comparator.comparingLong(character -> character.id().value())).toList();
    }

    @Override
    public CharacterCreationTransaction beginCreation(AccountId accountId) {
        transactions++;
        onBegin.run();
        fail();
        return new TestCharacterTransaction(this, accountId);
    }

    void fail() {
        if (failure != null) {
            throw failure;
        }
    }
}

package taan.online.character.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterBalanceSnapshot;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;
import taan.online.inventory.model.CharacterInventory;

public final class TestCharacterRepository implements CharacterRepository {
    public final List<GameCharacter> stored = new ArrayList<>();
    public final List<GameCharacter> archived = new ArrayList<>();
    public AccountGender gender = AccountGender.FEMALE;
    public boolean conflict;
    public long cash;
    public RuntimeException failure;
    public List<CharacterInventory> inventoryData = List.of();
    public Runnable onInventories = () -> { };
    public Runnable onRead = () -> { };
    public Runnable onBegin = () -> { };
    public Runnable onInsert = () -> { };
    public Runnable onArchive = () -> { };
    public Runnable onCommit = () -> { };
    public int reads;
    public int checks;
    public int transactions;
    public int inserts;
    public int commits;
    public int rollbacks;
    public int closes;

    @Override
    public Optional<CharacterBalanceSnapshot> findBalances(AccountId owner, CharacterId characterId) {
        reads++;
        onRead.run();
        fail();
        return stored.stream().filter(c -> c.accountId().equals(owner) && c.id().equals(characterId))
            .findFirst().map(c -> new CharacterBalanceSnapshot(c.id(), cash, c.gold()));
    }

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
    public CharacterTransaction beginTransaction(AccountId accountId) {
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

package taan.online.character.repository;

import java.time.Instant;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.StarterEquipment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

final class TestCharacterTransaction implements CharacterTransaction {
    private final TestCharacterRepository repository;
    private final AccountId owner;
    private GameCharacter pending;
    private GameCharacter archived;
    private boolean committed;

    TestCharacterTransaction(TestCharacterRepository repository, AccountId owner) {
        this.repository = repository;
        this.owner = owner;
    }

    @Override
    public AccountGender accountGender() {
        return repository.gender;
    }

    @Override
    public boolean hasCharacters() {
        return repository.stored.stream().anyMatch(character -> character.accountId().equals(owner));
    }

    @Override
    public Optional<GameCharacter> tryInsert(
        CharacterName name,
        CharacterAppearance appearance,
        StarterEquipment starter
    ) {
        repository.inserts++;
        repository.onInsert.run();
        repository.fail();

        if (repository.conflict || repository.existsByName(name)) {
            return Optional.empty();
        }

        pending = new GameCharacter(
            new CharacterId(1000 + repository.stored.size()),
            owner,
            name,
            1,
            repository.gender,
            appearance,
            Instant.EPOCH
        );
        return Optional.of(pending);
    }

    @Override
    public List<CharacterInventory> inventories(CharacterId characterId) {
        repository.onInventories.run();
        repository.fail();
        return repository.inventoryData;
    }

    @Override
    public boolean archive(CharacterId characterId) {
        repository.onArchive.run();
        repository.fail();
        archived = repository.stored.stream().filter(
            character -> character.accountId().equals(owner) && character.id().equals(characterId)
        ).findFirst().orElse(null);
        return archived != null;
    }

    @Override
    public List<GameCharacter> catalog() {
        var values = new ArrayList<>(repository.findByAccount(owner));
        values.remove(archived);

        if (pending != null) {
            values.add(pending);
        }

        return List.copyOf(values);
    }

    @Override
    public void commit() {
        repository.onCommit.run();
        repository.fail();
        repository.commits++;
        if (pending != null) {
            repository.stored.add(pending);
        }

        if (archived != null) {
            repository.stored.remove(archived);
            repository.archived.add(archived);
        }

        committed = true;
    }

    @Override
    public void close() {
        repository.closes++;

        if (!committed) {
            repository.rollbacks++;
        }
    }
}

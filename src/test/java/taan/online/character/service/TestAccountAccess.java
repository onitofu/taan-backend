package taan.online.character.service;

import java.util.Optional;
import taan.online.character.model.CharacterId;
import java.util.UUID;
import taan.online.account.model.AccountId;

public final class TestAccountAccess implements CharacterSelectionAccess {
    public boolean active = true;
    public CharacterId selected;
    public Runnable onSelect = () -> { };
    public Runnable onSelectedRead = () -> { };
    public int calls;
    public Runnable onAccess = () -> { };
    public final AccountId owner = new AccountId(1);

    @Override
    public boolean trySelect(UUID sessionId, CharacterId characterId) {
        onSelect.run();

        if (!active || selected != null) {
            return false;
        }

        selected = characterId;
        return true;
    }

    @Override
    public Optional<CharacterId> selectedCharacter(UUID sessionId) {
        onSelectedRead.run();
        return active ? Optional.ofNullable(selected) : Optional.empty();
    }

    @Override
    public Optional<AccountId> authenticatedAccount(UUID sessionId) {
        calls++;
        onAccess.run();
        return active && selected == null ? Optional.of(owner) : Optional.empty();
    }
}

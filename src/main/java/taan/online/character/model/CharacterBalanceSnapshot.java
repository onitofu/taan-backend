package taan.online.character.model;

import java.util.Objects;

public record CharacterBalanceSnapshot(CharacterId characterId, long cash, long gold) {
    public CharacterBalanceSnapshot {
        Objects.requireNonNull(characterId);

        if (cash < 0 || cash > 0xffff_ffffL || gold < 0 || gold > 0xffff_ffffL) {
            throw new IllegalArgumentException("Character balances are outside the unsigned integer range");
        }
    }
}

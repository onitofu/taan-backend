package taan.online.character.model;

import java.time.Instant;
import java.util.Objects;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

public record GameCharacter(
    CharacterId id,
    AccountId accountId,
    CharacterName name,
    int level,
    AccountGender gender,
    CharacterAppearance appearance,
    Instant createdAt,
    CharacterProgress progress
) {
    public GameCharacter(
        CharacterId id,
        AccountId accountId,
        CharacterName name,
        int level,
        AccountGender gender,
        CharacterAppearance appearance,
        Instant createdAt
    ) {
        this(id, accountId, name, level, gender, appearance, createdAt, CharacterProgress.initial());
    }

    public GameCharacter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(gender);
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(progress);

        if (level < 1 || level > 0xffff) {
            throw new IllegalArgumentException("Character level is outside the wire range");
        }
    }

    @Override
    public String toString() {
        return "GameCharacter[id=" + id.value() + "]";
    }
}

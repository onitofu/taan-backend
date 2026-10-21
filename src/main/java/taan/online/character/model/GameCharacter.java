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
    CharacterProgress progress,
    long gold
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

    public GameCharacter(
        CharacterId id,
        AccountId accountId,
        CharacterName name,
        int level,
        AccountGender gender,
        CharacterAppearance appearance,
        Instant createdAt,
        CharacterProgress progress
    ) {
        this(id, accountId, name, level, gender, appearance, createdAt, progress, 0);
    }

    public GameCharacter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(gender);
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(progress);

        if (gold < 0 || gold > 0xffff_ffffL) {
            throw new IllegalArgumentException("Character gold is outside the unsigned integer range");
        }

        if (level < 1 || level > 109) {
            throw new IllegalArgumentException("Character level is outside the progression range");
        }
    }

    @Override
    public String toString() {
        return "GameCharacter[id=" + id.value() + "]";
    }
}

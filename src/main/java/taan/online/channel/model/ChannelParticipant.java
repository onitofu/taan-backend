package taan.online.channel.model;

import java.util.Objects;
import java.util.UUID;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;

public record ChannelParticipant(
    UUID sessionId,
    AccountId accountId,
    CharacterId characterId,
    CharacterName name,
    int level,
    AccountGender gender
) {
    public ChannelParticipant {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(characterId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(gender);

        if (level < 1 || level > 65535) {
            throw new IllegalArgumentException("Invalid participant level");
        }
    }
}

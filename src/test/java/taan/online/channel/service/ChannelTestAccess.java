package taan.online.channel.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.model.ChannelParticipant;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;

public final class ChannelTestAccess implements SelectedCharacterAccess {
    public final Map<UUID, ChannelParticipant> players = new HashMap<>();
    public Runnable onAccess = () -> { };

    public UUID add(long id, int level) {
        var session = UUID.randomUUID();
        players.put(session, participant(session, id, level));
        return session;
    }

    public static ChannelParticipant participant(UUID session, long id, int level) {
        return new ChannelParticipant(
            session,
            new AccountId(id),
            new CharacterId(id),
            new CharacterName("Hero" + id),
            level,
            AccountGender.MALE
        );
    }

    @Override
    public Optional<ChannelParticipant> selectedCharacter(UUID session) {
        onAccess.run();
        return Optional.ofNullable(players.get(session));
    }
}

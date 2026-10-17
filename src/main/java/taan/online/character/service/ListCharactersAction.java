package taan.online.character.service;

import static taan.online.logging.LogField.accountId;

import java.util.List;
import taan.online.account.model.AccountId;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.CharacterRepository;
import taan.online.logging.EventLogger;

public final class ListCharactersAction {
    private static final EventLogger LOG = EventLogger.forClass(ListCharactersAction.class);

    private final CharacterRepository characters;

    public ListCharactersAction(CharacterRepository characters) {
        this.characters = characters;
    }

    public List<GameCharacter> execute(AccountId owner) {
        var catalog = List.copyOf(characters.findByAccount(owner));
        LOG.log(CharacterLogEvent.CATALOG_LOADED, accountId(owner.value()));
        return catalog;
    }
}

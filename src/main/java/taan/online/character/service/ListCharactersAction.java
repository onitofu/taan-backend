package taan.online.character.service;

import static taan.online.logging.LogField.accountId;

import java.util.List;
import taan.online.account.model.AccountId;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.CharacterRepository;
import taan.online.inventory.model.ItemCatalog;
import taan.online.logging.EventLogger;

public final class ListCharactersAction {
    private static final EventLogger LOG = EventLogger.forClass(ListCharactersAction.class);

    private final CharacterRepository characters;
    private final ItemCatalog items;

    public ListCharactersAction(CharacterRepository characters, ItemCatalog items) {
        this.characters = characters;
        this.items = items;
    }

    public List<GameCharacter> execute(AccountId owner) {
        var catalog = List.copyOf(characters.findByAccount(owner));
        for (var character : catalog) {
            for (var part : character.appearance().parts()) {
                if (part.itemId() != 0 && !items.fits(part.itemId(), part.slot(), character.gender())) {
                    throw new IllegalArgumentException("Unsupported stored character equipment");
                }
            }
        }

        LOG.log(CharacterLogEvent.CATALOG_LOADED, accountId(owner.value()));
        return catalog;
    }
}

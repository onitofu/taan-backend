package taan.online.room.service;

import java.util.Optional;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.CharacterRepository;
import taan.online.inventory.model.ItemCatalog;

public final class RoomCharacterLoader {
    private final CharacterRepository characters;
    private final ItemCatalog items;

    public RoomCharacterLoader(CharacterRepository characters, ItemCatalog items) {
        this.characters = characters;
        this.items = items;
    }

    public Optional<GameCharacter> load(ChannelMembershipSnapshot context) {
        var actor = context.participant();

        try (var transaction = characters.beginTransaction(actor.accountId())) {
            var selected = transaction.catalog().stream().filter(
                value -> value.id().equals(actor.characterId())
            ).findFirst();

            if (selected.isPresent()) {
                var character = selected.orElseThrow();
                transaction.inventories(character.id()).forEach(value -> items.validate(value, character.gender()));
            }

            transaction.commit();
            return selected;
        }
    }
}

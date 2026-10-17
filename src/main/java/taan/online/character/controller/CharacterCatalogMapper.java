package taan.online.character.controller;

import java.util.List;
import taan.online.account.model.AccountGender;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.character.protocol.packet.CharacterCatalog;
import taan.online.character.protocol.packet.CharacterInfo;
import taan.online.character.protocol.packet.CharacterPart;

public final class CharacterCatalogMapper {
    public CharacterCatalog map(List<GameCharacter> characters) {
        var snapshots = List.copyOf(characters);
        var info = snapshots.stream().map(
            character -> new CharacterInfo(
                character.id().value(),
                character.name().value(),
                character.level(),
                0,
                gender(character.gender())
            )
        ).toList();
        var appearances = snapshots.stream().map(this::appearance).toList();
        return new CharacterCatalog(info, appearances);
    }

    private CharacterAppear appearance(GameCharacter character) {
        var parts = character.appearance().parts().stream().map(
            part -> new CharacterPart(part.itemId(), part.parameter0(), part.parameter1(), part.parameter2())
        ).toList();
        return new CharacterAppear(gender(character.gender()), 0, 0, parts);
    }

    private int gender(AccountGender gender) {
        return switch (gender) {
            case MALE -> 1;
            case FEMALE -> 2;
        };
    }
}

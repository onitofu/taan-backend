package taan.online.character.controller;

import java.util.List;
import taan.online.account.model.AccountGender;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.packet.CharacterCatalog;
import taan.online.character.protocol.packet.CharacterInfo;

public final class CharacterCatalogMapper {
    private final CharacterLevelTable levels;

    public CharacterCatalogMapper(CharacterLevelTable levels) {
        this.levels = levels;
    }

    public CharacterCatalog map(List<GameCharacter> characters) {
        var snapshots = List.copyOf(characters);
        var info = snapshots.stream().map(
            character -> new CharacterInfo(
                character.id().value(),
                character.name().value(),
                character.level(),
                character.gold(),
                gender(character.gender()),
                character.progress().forceMedals(),
                character.progress().killMedals(),
                character.progress().supplyMedals(),
                character.progress().experience(),
                levels.displayStart(character.level()),
                levels.displayNext(character.level())
            )
        ).toList();
        var appearances = snapshots.stream().map(new CharacterAppearanceMapper()::appearance).toList();
        return new CharacterCatalog(info, appearances);
    }

    private int gender(AccountGender gender) {
        return switch (gender) {
            case MALE -> 1;
            case FEMALE -> 2;
        };
    }
}

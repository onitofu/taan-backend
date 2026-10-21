package taan.online.character.controller;

import taan.online.account.model.AccountGender;
import taan.online.character.model.GameCharacter;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.character.protocol.packet.CharacterPart;

public final class CharacterAppearanceMapper {
    public CharacterAppear appearance(GameCharacter character) {
        var parts = character.appearance().parts().stream().map(
            part -> new CharacterPart(part.itemId(), part.palette1Id(), part.palette2Id(), part.palette3Id())
        ).toList();
        return new CharacterAppear(character.gender() == AccountGender.MALE ? 1 : 2, 0, 0, parts);
    }
}

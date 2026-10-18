package taan.online.room.protocol.packet;

import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.protocol.option.packet.OptionList;

public record GameSlotOccupant(
    long accountId,
    long characterId,
    String name,
    String affiliationText,
    int level,
    int team,
    CharacterAppear appearance,
    int weaponId,
    OptionList options
) {
    public GameSlotOccupant(
        long accountId,
        long characterId,
        String name,
        String affiliationText,
        int level,
        int team,
        CharacterAppear appearance,
        int weaponId
    ) {
        this(accountId, characterId, name, affiliationText, level, team, appearance, weaponId, null);
    }
}

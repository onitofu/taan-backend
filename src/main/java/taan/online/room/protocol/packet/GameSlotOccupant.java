package taan.online.room.protocol.packet;

import taan.online.character.protocol.packet.CharacterAppear;

public record GameSlotOccupant(
    long accountId,
    long characterId,
    String name,
    String affiliationText,
    int level,
    int team,
    CharacterAppear appearance,
    int weaponId
) {
}

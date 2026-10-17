package taan.online.character.protocol.packet;

public record PqDelCharacter(int localSlot, long characterId) {
    public static final int TYPE_ID = 0x26d30563;
}

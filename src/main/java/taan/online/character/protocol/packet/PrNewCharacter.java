package taan.online.character.protocol.packet;

public record PrNewCharacter(int result, int localSlot, CharacterCatalog catalog) {
    public static final int TYPE_ID = 0x27c60579;
}

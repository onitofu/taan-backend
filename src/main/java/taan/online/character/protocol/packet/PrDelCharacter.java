package taan.online.character.protocol.packet;

public record PrDelCharacter(int result, int localSlot, CharacterCatalog catalog) {
    public static final int TYPE_ID = 0x26e00564;
}
